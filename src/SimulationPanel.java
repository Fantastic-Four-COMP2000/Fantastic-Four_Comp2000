import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimulationPanel extends Panel implements Runnable {

    private ArrayList<Person> people = new ArrayList<>();
    private ArrayList<RecoveryTent> tents = new ArrayList<>();

    private Virus virus = new Virus("Zombie Virus", 45);
    private Hospital hospital;

    // The selected infection rule and statistics mode.
    private final InfectionStrategy infectionStrategy;
    private final boolean parallelStatistics;

    private Thread thread;

    // stopSimulation() and run() access this from different threads.
    private volatile boolean running;

    // Tracks whether treatment buildings are currently available.
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

    private static final long DEATH_TIME = 50000;
    private static final long RECOVER_TO_HEALTHY_TIME = 10000;

    // Each initially healthy person has a 5% chance of vaccination.
    private static final double VACCINATION_RATE = 0.05;

    // Immunity blocks an infection attempt with a 20% probability.
    private static final double VACCINE_EFFECTIVENESS = 0.2;

    /*
     * The simulation publishes a completed, unmodifiable map.
     * volatile makes the replacement visible to the drawing thread.
     */
    private volatile Map<String, Long> statusCounts = Map.of();

    // Keeps the original age-based behaviour as the default.
    public SimulationPanel() {
        this(new AgeBasedInfectionStrategy(), false);
    }

    // Receives the infection rule and statistics mode from Main.
    public SimulationPanel(
            InfectionStrategy infectionStrategy,
            boolean parallelStatistics) {

        if (infectionStrategy == null) {
            throw new IllegalArgumentException(
                    "Infection strategy cannot be null."
            );
        }

        this.infectionStrategy = infectionStrategy;
        this.parallelStatistics = parallelStatistics;

        setBackground(Color.LIGHT_GRAY);

        checkStatisticsCalculation();
        createPeople();
        updateStatusCounts();
    }

    // Creates the initial population.
    private void createPeople() {
        for (int i = 0; i < TOTAL_PEOPLE; i++) {
            String name = "Person " + (i + 1);
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

    private int randomBuildingY(int objectHeight) {
        return 150 + randomPosition(
                Math.max(100, getHeight() - 150),
                objectHeight
        );
    }

    // Creates the buildings and starts the simulation loop.
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

    // Updates movement and health before calculating the new counts.
    private void updateSimulation() {
        long now = System.currentTimeMillis();

        updateBuildings(now);

        for (Person person : people) {
            person.move(getWidth(), getHeight());
        }

        // These health rules are checked once per second.
        if (now - lastHealthCheck >= 1000) {
            spreadInfection();
            naturalRecovery(now);
            checkDeaths(now);
            recoveredToHealthy(now);

            lastHealthCheck = now;
        }

        buildingTreatment(now);
        vaccinateAtHospital(now);

        updateStatusCounts();
    }

    /*
     * Capture status values on the simulation thread first.
     * Parallel workers only receive Strings, not live Person objects.
     *
     * Vaccinated people remain part of the Healthy count.
     */
    private void updateStatusCounts() {
        List<String> statuses = people.stream()
                .map(Person::getStatus)
                .collect(Collectors.toUnmodifiableList());

        Map<String, Long> counts =
                countStatuses(statuses, parallelStatistics);

        statusCounts = Map.copyOf(counts);
    }

    /*
     * Selects sequential or parallel processing.
     * groupingBy() creates the groups and counting() counts each group.
     * No shared counter is updated manually.
     */
    private Map<String, Long> countStatuses(
            List<String> statuses,
            boolean parallel) {

        Stream<String> stream;

        if (parallel) {
            stream = statuses.parallelStream();
        } else {
            stream = statuses.stream();
        }

        return stream.collect(Collectors.groupingBy(
                status -> status,
                Collectors.counting()
        ));
    }

    // Checks both calculation modes against known expected results.
    private void checkStatisticsCalculation() {
        List<String> sample = List.of(
                "Healthy",
                "Infected",
                "Healthy",
                "Recovered"
        );

        Map<String, Long> expected = Map.of(
                "Healthy", 2L,
                "Infected", 1L,
                "Recovered", 1L
        );

        Map<String, Long> sequential =
                countStatuses(sample, false);

        Map<String, Long> parallel =
                countStatuses(sample, true);

        if (!expected.equals(sequential)
                || !expected.equals(parallel)) {

            throw new IllegalStateException(
                    "Health status counting produced incorrect results."
            );
        }

        List<String> empty = List.of();

        if (!countStatuses(empty, false).isEmpty()
                || !countStatuses(empty, true).isEmpty()) {

            throw new IllegalStateException(
                    "An empty population should produce no status counts."
            );
        }
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

    // Buildings appear and disappear at different intervals.
    private void updateBuildings(long now) {
        if (tentsActive && now - tentStart >= 5000) {
            tentsActive = false;
            tents.clear();
        }

        if (!tentsActive && now - lastTent >= 10000) {
            spawnTents();

            tentsActive = true;
            tentStart = now;
            lastTent = now;
        }

        if (hospitalActive && now - hospitalStart >= 3000) {
            hospitalActive = false;
        }

        if (!hospitalActive && now - lastHospital >= 15000) {
            spawnHospital();

            hospitalActive = true;
            hospitalStart = now;
            lastHospital = now;
        }
    }

    private void spreadInfection() {
        /*
         * Health transitions replace objects in the people list.
         * This copy prevents newly infected people from spreading
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

                // Preserve the existing immunity protection rule.
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

    /*
     * Strategy pattern: the panel delegates the decision.
     * Age and exposure rules belong to the selected strategy.
     */
    private boolean shouldInfect(Healthy person) {
        return infectionStrategy.shouldInfect(person, virus);
    }

    // Preserves identity, position and movement during the transition.
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

    private void naturalRecovery(long now) {
        for (int i = 0; i < people.size(); i++) {
            if (!(people.get(i) instanceof Infected)) {
                continue;
            }

            Infected infected = (Infected) people.get(i);

            long infectedTime =
                    now - infected.getInfectedSince();

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

    // Healthy people receive vaccination after two seconds inside.
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

    // Treatment depends on how long the infected person stays inside.
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

                // Tents require two seconds of continuous treatment.
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

                // Hospital treatment requires one second.
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

    private void drawStats(Graphics g) {
        /*
         * Read the reference once so all displayed values
         * come from the same completed statistics update.
         */
        Map<String, Long> counts = statusCounts;

        int healthy =
                counts.getOrDefault("Healthy", 0L).intValue();

        int infected =
                counts.getOrDefault("Infected", 0L).intValue();

        int recovered =
                counts.getOrDefault("Recovered", 0L).intValue();

        int dead =
                counts.getOrDefault("Dead", 0L).intValue();

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