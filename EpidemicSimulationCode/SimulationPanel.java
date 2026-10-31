import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class SimulationPanel extends Panel implements Runnable {

    private ArrayList<Person> people = new ArrayList<>();
    private ArrayList<RecoveryTent> tents = new ArrayList<>();

    private Virus virus = new Virus("Zombie Virus", 45);
    private Hospital hospital;

    // Controls the simulation thread.
    private Thread thread;
    private boolean running;

    // Shows whether treatment buildings are currently available.
    private boolean tentsActive;
    private boolean hospitalActive;

    private long lastTent;
    private long tentStart;

    private long lastHospital;
    private long hospitalStart;

    private long lastHealthCheck;

    private static final int TOTAL_PEOPLE = 100;
    private static final int START_INFECTED = 5;

    // People within this distance can pass on the infection.
    private static final double CONTACT_DISTANCE = 18;

    // An infected person dies after 50 seconds without recovery.
    private static final long DEATH_TIME = 50000;

    // Recovered people become healthy with immunity after 10 seconds.
    private static final long RECOVER_TO_HEALTHY_TIME = 10000;

    // Each initially healthy person has a 5% chance of vaccination.
    private static final double VACCINATION_RATE = 0.05;

    // Immunity has a 20% chance of blocking an infection attempt.
    private static final double VACCINE_EFFECTIVENESS = 0.2;

    /*
     * The simulation thread calculates the counts.
     * The drawing thread reads the latest completed map.
     *
     * volatile makes the replacement map visible to the drawing thread.
     * Each published map is unmodifiable.
     */
    private volatile Map<String, Long> statusCounts = Map.of();

    public SimulationPanel() {
        setBackground(Color.LIGHT_GRAY);
        createPeople();
        updateStatusCounts();
    }

    // Creates the starting population.
    private void createPeople() {
        for (int i = 0; i < TOTAL_PEOPLE; i++) {
            String name = "Person " + (i + 1);

            // Random age between 18 and 80, inclusive.
            int age = 18 + (int) (Math.random() * 63);

            if (i < START_INFECTED) {
                people.add(new Infected(
                        name,
                        age,
                        virus,
                        randomX(),
                        randomY(),
                        randomSpeed(),
                        randomSpeed()
                ));
            } else {
                boolean vaccinated =
                        Math.random() < VACCINATION_RATE;

                people.add(new Healthy(
                        name,
                        age,
                        randomX(),
                        randomY(),
                        randomSpeed(),
                        randomSpeed(),
                        vaccinated
                ));
            }
        }
    }

    private float randomX() {
        return (float) (Math.random() * 850);
    }

    private float randomY() {
        return 160 + (float) (Math.random() * 400);
    }

    private float randomSpeed() {
        float speed = (float) (Math.random() * 4 - 2);

        // Avoid movement that is too slow to notice.
        if (Math.abs(speed) < 0.4f) {
            speed = 0.8f;
        }

        return speed;
    }

    private int randomPosition(int size, int objectSize) {
        return (int) (
                Math.random() * Math.max(1, size - objectSize)
        );
    }

    // Keeps buildings below the information panels.
    private int randomBuildingY(int objectHeight) {
        return 150 + randomPosition(
                Math.max(100, getHeight() - 150),
                objectHeight
        );
    }

    // Creates the buildings and starts the simulation.
    public void startSimulation() {
        if (running) {
            return;
        }

        spawnTents();
        spawnHospital();

        long now = System.currentTimeMillis();

        tentsActive = true;
        hospitalActive = true;

        tentStart = now;
        lastTent = now;

        hospitalStart = now;
        lastHospital = now;

        lastHealthCheck = now;

        running = true;

        thread = new Thread(this);
        thread.start();
    }

    public void stopSimulation() {
        running = false;

        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running) {
            updateSimulation();
            repaint();

            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
            }
        }
    }

    // Updates the model before requesting another drawing.
    private void updateSimulation() {
        long now = System.currentTimeMillis();

        updateBuildings(now);

        for (Person person : people) {
            person.move(getWidth(), getHeight());
        }

        // Infection and natural health changes are checked once per second.
        if (now - lastHealthCheck >= 1000) {
            spreadInfection();
            naturalRecovery(now);
            checkDeaths(now);
            recoveredToHealthy(now);

            lastHealthCheck = now;
        }

        buildingTreatment(now);
        vaccinateAtHospital(now);

        // Calculate statistics after all changes for this update.
        updateStatusCounts();
    }

    /*
     * Groups people by the status returned by their subclass.
     * counting() gives the number of people in each group.
     *
     * For example:
     * Healthy -> 95
     * Infected -> 5
     *
     * Vaccinated people still belong to the Healthy group.
     */
    private void updateStatusCounts() {
        Map<String, Long> counts = people.stream()
                .collect(Collectors.groupingBy(
                        Person::getStatus,
                        Collectors.counting()
                ));

        // Publish a completed map that cannot be modified.
        statusCounts = Map.copyOf(counts);
    }

    private void spawnTents() {
        tents.clear();

        for (int i = 0; i < 3; i++) {
            tents.add(new RecoveryTent(
                    randomPosition(getWidth(), 100),
                    randomBuildingY(100),
                    100,
                    100
            ));
        }
    }

    private void spawnHospital() {
        hospital = new Hospital(
                randomPosition(getWidth(), 120),
                randomBuildingY(100),
                120,
                100
        );
    }

    // Buildings disappear and return at different intervals.
    private void updateBuildings(long now) {

        // Tents remain available for five seconds.
        if (tentsActive && now - tentStart >= 5000) {
            tentsActive = false;
            tents.clear();
        }

        // Tents return ten seconds after their previous appearance.
        if (!tentsActive && now - lastTent >= 10000) {
            spawnTents();

            tentsActive = true;
            tentStart = now;
            lastTent = now;
        }

        // The hospital remains available for three seconds.
        if (hospitalActive && now - hospitalStart >= 3000) {
            hospitalActive = false;
        }

        // The hospital returns every fifteen seconds.
        if (!hospitalActive && now - lastHospital >= 15000) {
            spawnHospital();

            hospitalActive = true;
            hospitalStart = now;
            lastHospital = now;
        }
    }

    // Checks whether infected people can infect nearby healthy people.
    private void spreadInfection() {

        /*
         * Transitions replace objects in the people list.
         * This copy keeps newly infected people from spreading
         * the virus during the same infection check.
         */
        ArrayList<Person> snapshot = new ArrayList<>(people);

        for (Person infected : snapshot) {
            if (!(infected instanceof Infected)) {
                continue;
            }

            for (int i = 0; i < people.size(); i++) {
                Person target = people.get(i);

                if (!(target instanceof Healthy)) {
                    continue;
                }

                Healthy healthy = (Healthy) target;

                // Immunity reduces infection risk without guaranteeing protection.
                if (healthy.isImmune()
                        && Math.random() < VACCINE_EFFECTIVENESS) {
                    continue;
                }

                if (distance(infected, healthy) <= CONTACT_DISTANCE) {
                    if (shouldInfect(healthy)) {
                        infect(i, healthy);
                    }
                }
            }
        }
    }

    private double distance(Person first, Person second) {
        float dx = first.getX() - second.getX();
        float dy = first.getY() - second.getY();

        return Math.sqrt(dx * dx + dy * dy);
    }

    // Applies the simulation's age-based infection rules.
    private boolean shouldInfect(Healthy person) {
        int chance = virus.getSpreadPercentage();

        if (person.getAge() < 30) {
            person.addExposure();

            // Younger people need at least two close contacts.
            if (person.getExposureCount() < 2) {
                return false;
            }

            chance -= 20;
        } else if (person.getAge() > 50) {
            chance += 25;
        }

        // Keep the final infection chance between 5% and 90%.
        chance = Math.max(5, Math.min(chance, 90));

        return Math.random() * 100 < chance;
    }

    // Changes health status while preserving identity and movement.
    private void infect(int index, Healthy person) {
        people.set(index, new Infected(
                person.getId(),
                person.getName(),
                person.getAge(),
                virus,
                person.getX(),
                person.getY(),
                person.getSpeedX(),
                person.getSpeedY()
        ));
    }

    // Gives infected people a chance to recover without treatment.
    private void naturalRecovery(long now) {
        for (int i = 0; i < people.size(); i++) {
            if (!(people.get(i) instanceof Infected)) {
                continue;
            }

            Infected infected = (Infected) people.get(i);

            long infectedTime = now - infected.getInfectedSince();

            // Natural recovery starts after fifteen seconds.
            if (infectedTime < 15000) {
                continue;
            }

            int chance;

            if (infected.getAge() < 30) {
                chance = 18;
            } else if (infected.getAge() > 50) {
                chance = 6;
            } else {
                chance = 10;
            }

            if (Math.random() * 100 < chance) {
                recover(i, infected);
            }
        }
    }

    // Healthy people receive vaccination after two seconds in the hospital.
    private void vaccinateAtHospital(long now) {
        if (!hospitalActive || hospital == null) {
            return;
        }

        for (int i = 0; i < people.size(); i++) {
            if (!(people.get(i) instanceof Healthy)) {
                continue;
            }

            Healthy healthy = (Healthy) people.get(i);

            if (healthy.isImmune()) {
                continue;
            }

            if (hospital.isPersonInside(healthy)) {
                if (healthy.getHospitalEntryTime() == -1) {
                    healthy.setHospitalEntryTime(now);
                }

                if (now - healthy.getHospitalEntryTime() >= 2000) {
                    healthy.setImmune(true);
                }
            } else {
                healthy.setHospitalEntryTime(-1);
            }
        }
    }

    // Checks whether infected people have stayed in treatment long enough.
    private void buildingTreatment(long now) {
        for (int i = 0; i < people.size(); i++) {
            if (!(people.get(i) instanceof Infected)) {
                continue;
            }

            Infected infected = (Infected) people.get(i);

            boolean insideTent = false;

            if (tentsActive) {
                for (RecoveryTent tent : tents) {
                    if (tent.isPersonInside(infected)) {
                        insideTent = true;
                        break;
                    }
                }
            }

            if (insideTent) {
                if (infected.getTentEntryTime() == -1) {
                    infected.setTentEntryTime(now);
                }

                // Recovery tents require two seconds of continuous treatment.
                if (now - infected.getTentEntryTime() >= 2000) {
                    recover(i, infected);
                    continue;
                }
            } else {
                infected.setTentEntryTime(-1);
            }

            if (hospitalActive
                    && hospital != null
                    && hospital.isPersonInside(infected)) {

                if (infected.getHospitalEntryTime() == -1) {
                    infected.setHospitalEntryTime(now);
                }

                // Hospital treatment takes one second.
                if (now - infected.getHospitalEntryTime() >= 1000) {
                    recover(i, infected);
                }
            } else {
                infected.setHospitalEntryTime(-1);
            }
        }
    }

    private void recover(int index, Infected infected) {
        people.set(index, new Recovered(
                infected.getId(),
                infected.getName(),
                infected.getAge(),
                infected.getX(),
                infected.getY(),
                infected.getSpeedX(),
                infected.getSpeedY()
        ));
    }

    // Recovered people eventually return to Healthy with immunity.
    private void recoveredToHealthy(long now) {
        for (int i = 0; i < people.size(); i++) {
            if (!(people.get(i) instanceof Recovered)) {
                continue;
            }

            Recovered recovered = (Recovered) people.get(i);

            if (now - recovered.getRecoveredSince()
                    >= RECOVER_TO_HEALTHY_TIME) {

                people.set(i, new Healthy(
                        recovered.getId(),
                        recovered.getName(),
                        recovered.getAge(),
                        recovered.getX(),
                        recovered.getY(),
                        recovered.getSpeedX(),
                        recovered.getSpeedY(),
                        true
                ));
            }
        }
    }

    private void checkDeaths(long now) {
        for (int i = 0; i < people.size(); i++) {
            if (!(people.get(i) instanceof Infected)) {
                continue;
            }

            Infected infected = (Infected) people.get(i);

            if (now - infected.getInfectedSince() >= DEATH_TIME) {
                people.set(i, new Dead(
                        infected.getId(),
                        infected.getName(),
                        infected.getAge(),
                        infected.getX(),
                        infected.getY()
                ));
            }
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        drawBuildings(g);
        drawPeople(g);
        drawLegend(g);
        drawStats(g);
    }

    private void drawBuildings(Graphics g) {
        if (tentsActive) {
            for (RecoveryTent tent : tents) {
                g.setColor(Color.ORANGE);

                g.fillRect(
                        tent.getX(),
                        tent.getY(),
                        tent.getWidth(),
                        tent.getHeight()
                );

                g.setColor(Color.BLACK);

                g.drawString(
                        "Recovery Tent",
                        tent.getX() + 5,
                        tent.getY() + 20
                );
            }
        }

        if (hospitalActive && hospital != null) {
            g.setColor(Color.WHITE);

            g.fillRect(
                    hospital.getX(),
                    hospital.getY(),
                    hospital.getWidth(),
                    hospital.getHeight()
            );

            g.setColor(Color.BLACK);

            g.drawRect(
                    hospital.getX(),
                    hospital.getY(),
                    hospital.getWidth(),
                    hospital.getHeight()
            );

            g.drawString(
                    "Hospital",
                    hospital.getX() + 30,
                    hospital.getY() + 20
            );
        }
    }

    private void drawPeople(Graphics g) {
        for (Person person : people) {
            if (person instanceof Healthy
                    && ((Healthy) person).isImmune()) {

                g.setColor(Color.CYAN);
            } else {
                switch (person.getStatus()) {
                    case "Healthy":
                        g.setColor(Color.GREEN);
                        break;

                    case "Infected":
                        g.setColor(Color.RED);
                        break;

                    case "Recovered":
                        g.setColor(Color.BLUE);
                        break;

                    default:
                        g.setColor(Color.BLACK);
                        break;
                }
            }

            g.fillOval(
                    (int) person.getX(),
                    (int) person.getY(),
                    15,
                    15
            );
        }
    }

    private void drawLegend(Graphics g) {
        int x = 15;
        int y = 15;

        g.setColor(Color.WHITE);
        g.fillRect(x, y, 180, 145);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, 180, 145);

        g.drawString("COLOR GUIDE", x + 15, y + 20);

        drawLegendItem(g, Color.GREEN, "Healthy", x, y + 40);
        drawLegendItem(g, Color.RED, "Infected", x, y + 60);
        drawLegendItem(g, Color.BLUE, "Recovered", x, y + 80);
        drawLegendItem(g, Color.BLACK, "Dead", x, y + 100);
        drawLegendItem(g, Color.CYAN, "Vaccinated", x, y + 120);
    }

    private void drawLegendItem(
            Graphics g,
            Color color,
            String text,
            int x,
            int y) {

        g.setColor(color);
        g.fillOval(x + 15, y - 10, 12, 12);

        g.setColor(Color.BLACK);
        g.drawString(text, x + 35, y);
    }

    // Displays the counts already calculated by the simulation.
    private void drawStats(Graphics g) {

        /*
         * Read the map reference once so all displayed counts
         * come from the same completed update.
         */
        Map<String, Long> counts = statusCounts;

        // A status may be absent when nobody currently has that status.
        int healthy = counts.getOrDefault("Healthy", 0L).intValue();
        int infected = counts.getOrDefault("Infected", 0L).intValue();
        int recovered = counts.getOrDefault("Recovered", 0L).intValue();
        int dead = counts.getOrDefault("Dead", 0L).intValue();

        int total = healthy + infected + recovered + dead;

        int x = Math.max(10, getWidth() - 245);

        g.setColor(Color.WHITE);
        g.fillRect(x, 10, 230, 125);

        g.setColor(Color.BLACK);
        g.drawRect(x, 10, 230, 125);

        g.drawString("EPIDEMIC STATUS", x + 15, 30);
        g.drawString("Total People: " + total, x + 15, 50);

        g.drawString(
                String.format(
                        "Healthy: %d (%.1f%%)",
                        healthy,
                        percent(healthy, total)
                ),
                x + 15,
                70
        );

        g.drawString(
                String.format(
                        "Infected: %d (%.1f%%)",
                        infected,
                        percent(infected, total)
                ),
                x + 15,
                88
        );

        g.drawString(
                String.format(
                        "Recovered: %d (%.1f%%)",
                        recovered,
                        percent(recovered, total)
                ),
                x + 15,
                106
        );

        g.drawString(
                String.format(
                        "Dead: %d (%.1f%%)",
                        dead,
                        percent(dead, total)
                ),
                x + 15,
                124
        );
    }

    private double percent(int amount, int total) {
        if (total == 0) {
            return 0;
        }

        return amount * 100.0 / total;
    }
}