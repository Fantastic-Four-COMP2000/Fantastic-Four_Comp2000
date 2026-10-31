import java.awt.*;
import java.util.ArrayList;

public class SimulationPanel extends Panel implements Runnable {

    private ArrayList<Person> people = new ArrayList<>();
    private ArrayList<RecoveryTent> tents = new ArrayList<>();

    private Virus virus = new Virus("Zombie Virus", 45);
    private Hospital hospital;

    // Controls the simulation loop
    private Thread thread;
    private boolean running;

    // Tracks whether treatment buildings are currently visible
    private boolean tentsActive;
    private boolean hospitalActive;

    private long lastTent;
    private long tentStart;

    private long lastHospital;
    private long hospitalStart;

    private long lastHealthCheck;

    private static final int TOTAL_PEOPLE = 100;
    private static final int START_INFECTED = 5;

    // 18 pixels represents about 5mm close contact
    private static final double CONTACT_DISTANCE = 18;

    // Time an infected person can survive before dying
    private static final long DEATH_TIME = 50000;

    // Time before a recovered person becomes healthy and immune
    private static final long RECOVER_TO_HEALTHY_TIME = 10000;

    // Fraction of the initial population that starts vaccinated/immune
    private static final double VACCINATION_RATE = 0.05;

    // Chance that immunity actually blocks an infection attempt (not 100%)
    private static final double VACCINE_EFFECTIVENESS = 0.2; // 30%
    
    public SimulationPanel() {
        setBackground(Color.LIGHT_GRAY);
        createPeople();
    }

    
    // Creates the initial population with a small number already infected and a few immune
    private void createPeople() {
        for (int i = 0; i < TOTAL_PEOPLE; i++) {
        String name = "Person " + (i + 1);
         // Random age: 18 - 80
            int age = 18 + (int)(Math.random() * 63);

            if (i < START_INFECTED) {
                people.add(new Infected(
                    name,age,virus,randomX(),randomY(),randomSpeed(),randomSpeed()));

            } else {
                boolean vaccinated = Math.random() < VACCINATION_RATE;
                people.add(new Healthy(name,age,randomX(),randomY(),randomSpeed(),randomSpeed(),vaccinated));
            }
        }
    }
        private float randomX() {
        return (float)(Math.random() * 850);
    }
        private float randomY() {
        return 160 + (float)(Math.random() * 400);
    }
        private float randomSpeed() {
        float speed = (float)(Math.random() * 4 - 2);

        if (Math.abs(speed) < 0.4f) {
            speed = 0.8f;
        }
        return speed;
    }
    private int randomPosition(
            int size,
            int objectSize) {
        return (int)(Math.random()* Math.max(1, size - objectSize));
    }


    private int randomBuildingY(
            int objectHeight) {
    return 150 +randomPosition(Math.max(100,getHeight() - 150),objectHeight);
    }

    // Initializes buildings and starts the simulation thread
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

    // Main simulation loop that repeatedly updates and redraws the simulation
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
    
    // Updates movement, health conditions and building treatment
    private void updateSimulation() {
        long now = System.currentTimeMillis();
            updateBuildings(now);
         for (Person person : people) {
            person.move(getWidth(), getHeight());
        }

        // Health rules checked once per second
        if (now - lastHealthCheck >= 1000) {

            spreadInfection();
            naturalRecovery(now);
            checkDeaths(now);
            recoveredToHealthy(now);
             lastHealthCheck = now;
        }

        buildingTreatment(now);
        vaccinateAtHospital(now);
    }

    // Creates three recovery tents at random locations
    private void spawnTents() {
        tents.clear();
        for (int i = 0; i < 3; i++) {
        tents.add(new RecoveryTent(randomPosition(getWidth(), 100),randomBuildingY(100),100,100));
        }
    }

    // Creates the hospital at a random location
    private void spawnHospital() {
        hospital = new Hospital(randomPosition(getWidth(), 120),randomBuildingY(100),120,100);
    }


    private void updateBuildings(long now) {

        // Recovery tents stay 5 sec
        if (tentsActive && now - tentStart >= 5000) {

            tentsActive = false;
            tents.clear();
        }

        // Return after another 5 sec
        if (!tentsActive && now - lastTent >= 10000) {

            spawnTents();

            tentsActive = true;
            tentStart = now;
            lastTent = now;
        }


        // Hospital stays 3 sec
        if (hospitalActive && now - hospitalStart >= 3000) {
            hospitalActive = false;
        }

        // Hospital returns every 15 sec
        if (!hospitalActive&& now - lastHospital >= 15000) {

            spawnHospital();
            hospitalActive = true;
            hospitalStart = now;
            lastHospital = now;
        }
    }


    // Checks close contact between infected and healthy people
    // and attempts to spread the virus
    private void spreadInfection() {

        // Snapshot stops new infections spreading instantly
        ArrayList<Person> snapshot = new ArrayList<>(people);

        for (Person infected : snapshot) {
             
            // Only infected people can spread the virus
            if (!(infected instanceof Infected)) {
                continue;
            }

            for (int i = 0; i < people.size(); i++) {

                Person target = people.get(i);
                 
                // Only healthy people can become infected
                if (!(target instanceof Healthy)) {
                    continue;
                }

                Healthy healthy = (Healthy) target;

                // Recovered people become immune healthy
                if (healthy.isImmune() && Math.random() < VACCINE_EFFECTIVENESS) {
                    continue;
                }
             if (distance(infected, healthy)<= CONTACT_DISTANCE) {
                    if (shouldInfect(healthy)) {
                        infect(i, healthy);
                    }
                }
            }
        }
    }


    private double distance(Person first,Person second) {
      float dx =first.getX() - second.getX();
        float dy =first.getY() - second.getY();

        return Math.sqrt(dx * dx + dy * dy);
    }


    private boolean shouldInfect(
            Healthy person) {

        int chance = virus.getSpreadPercentage();
        // Under 30:
        // two close contacts required
        if (person.getAge() < 30) {

            person.addExposure();

            if (person.getExposureCount() < 2) {
                return false;
            }

            chance -= 20;
        }

        // Over 50:
        // more likely to become infected
        else if (person.getAge() > 50) {

            chance += 25;
        }

        chance =Math.max(5,Math.min(chance, 90));

        return Math.random() * 100 < chance;
    }


    private void infect(int index,Healthy person) {

        people.set(index,new Infected(
                        person.getId(),
                        person.getName(),
                        person.getAge(),
                        virus,
                        person.getX(),
                        person.getY(),
                        person.getSpeedX(),
                        person.getSpeedY()));
    }

    // Gives infected people a chance to recover naturally
    // based on their age and infection duration
    private void naturalRecovery(long now) {

        for (int i = 0; i < people.size(); i++) {

            if (!(people.get(i) instanceof Infected)) {
                continue;
            }

            Infected infected =
                    (Infected) people.get(i);

            long infectedTime =
                    now - infected.getInfectedSince();

            // No natural recovery before 15 sec
            if (infectedTime < 15000) {
                continue;
            }

            int chance;

            if (infected.getAge() < 30) {
                chance = 18;
            }

            else if (infected.getAge() > 50) {
                chance = 6;
            }

            else {
                chance = 10;
            }

            if (Math.random() * 100 < chance) {
                recover(i, infected);
            }
        }
    }

    // healthy people who stay in the hospital long enough get vaccinated
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

    // Checks whether infected people remain inside treatment
    // buildings long enough to recover
    private void buildingTreatment(long now) {

        for (int i = 0; i < people.size(); i++) {

            if (!(people.get(i) instanceof Infected)) {
                continue;
            }

            Infected infected =(Infected) people.get(i);


            // Recovery Tent
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
                 
                // Start the treatment timer when entering a tent
                if (infected.getTentEntryTime() == -1) {
                    infected.setTentEntryTime(now);
                }
                 
                // Recover after remaining inside the tent for 2 seconds
                if (now- infected.getTentEntryTime() >= 2000) {

                    recover(i, infected);

                    continue;
                }

            } else {
                // Leaving the tent resets the treatment timer
                infected.setTentEntryTime(-1);
            }


            // Hospital
            if (hospitalActive && hospital != null&& hospital.isPersonInside(infected)) {

                if (infected.getHospitalEntryTime() == -1) {
                    infected.setHospitalEntryTime(now);
                }

                if (now- infected.getHospitalEntryTime()>= 1000) {  
                        recover(i, infected);}

            } else {

                infected.setHospitalEntryTime(-1);
            }
        }
    }

    // Replaces an infected person with a recovered person
    // while preserving their identity, position and movement
    private void recover(
            int index,
            Infected infected) {

        people.set(index,new Recovered(
                        infected.getId(),
                        infected.getName(),
                        infected.getAge(),
                        infected.getX(),
                        infected.getY(),
                        infected.getSpeedX(),
                        infected.getSpeedY()));
    }


    // Converts recovered people back to healthy after the
    // recovery period and gives them immunity
    private void recoveredToHealthy(long now) {

        for (int i = 0; i < people.size(); i++) {

            if (!(people.get(i) instanceof Recovered)) {
                continue;
            }

            Recovered recovered = (Recovered) people.get(i);

            if (now - recovered.getRecoveredSince()>= RECOVER_TO_HEALTHY_TIME) {
                people.set(i,new Healthy(
                                recovered.getId(),
                                recovered.getName(),
                                recovered.getAge(),
                                recovered.getX(),
                                recovered.getY(),
                                recovered.getSpeedX(),
                                recovered.getSpeedY(),
                                true));
            }
        }
    }

    // Converts people who remain infected too long into dead people
    private void checkDeaths(long now) {

        for (int i = 0; i < people.size(); i++) {

            if (!(people.get(i) instanceof Infected)) {
                continue;
            }

            Infected infected = (Infected) people.get(i);

            // Person dies after reaching the maximum infection duration
            if (now - infected.getInfectedSince() >= DEATH_TIME) {

                people.set(i, new Dead(
                                infected.getId(),
                                infected.getName(),
                                infected.getAge(),
                                infected.getX(),
                                infected.getY()));
            }
        }
    }

    // Draws all visual elements of the simulation
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
                        tent.getHeight());

                g.setColor(Color.BLACK);

                g.drawString("Recovery Tent",tent.getX() + 5,tent.getY() + 20);
            }
        }


        if (hospitalActive && hospital != null) {

            g.setColor(Color.WHITE);

            g.fillRect(
                    hospital.getX(),
                    hospital.getY(),
                    hospital.getWidth(),
                    hospital.getHeight());

            g.setColor(Color.BLACK);

            g.drawRect(
                    hospital.getX(),
                    hospital.getY(),
                    hospital.getWidth(),
                    hospital.getHeight());

            g.drawString("Hospital",hospital.getX() + 30, hospital.getY() + 20);
        }
    }


    private void drawPeople(Graphics g) {
    for (Person person : people) {
        if (person instanceof Healthy && ((Healthy) person).isImmune()) {
            g.setColor(Color.CYAN);
        } else {
            switch (person.getStatus()) {
                case "Healthy":   
                    g.setColor(Color.GREEN); break;
                case "Infected":  
                    g.setColor(Color.RED);   break;
                case "Recovered": 
                    g.setColor(Color.BLUE);  break;
                default:         
                     g.setColor(Color.BLACK); break;
            }
        }
        g.fillOval((int) person.getX(), (int) person.getY(), 15, 15);
    }
}


    // Displays the colour guide for each health state
    private void drawLegend(Graphics g) {

        int x = 15;
        int y = 15;

        g.setColor(Color.WHITE);
        g.fillRect(x, y, 180, 145);

        g.setColor(Color.BLACK);
        g.drawRect(x, y, 180, 145);

        g.drawString("COLOR GUIDE",x + 15,y + 20);


        drawLegendItem(g,Color.GREEN,"Healthy",x,y + 40);

        drawLegendItem(
                g,Color.RED,"Infected",x,y + 60);

        drawLegendItem(
                g,Color.BLUE,"Recovered",x,y + 80);

        drawLegendItem(
                g,Color.BLACK,"Dead",x,y + 100);
        
        drawLegendItem(
            g,Color.CYAN,"Vaccinated",x,y + 120);
    }


    private void drawLegendItem(
            Graphics g,
            Color color,
            String text,
            int x,
            int y) {

        g.setColor(color);

        g.fillOval(x + 15,y - 10,12,12);

        g.setColor(Color.BLACK);

        g.drawString(text,x + 35,y);
    }

    // Counts each health state and displays the epidemic statistics
    private void drawStats(Graphics g) {

        int healthy = 0;
        int infected = 0;
        int recovered = 0;
        int dead = 0;


        // Polymorphism: all Person subclasses provide their own health status
        for (Person person : people) {

            switch (person.getStatus()) {
            case "Healthy":healthy++;break;

                case "Infected":infected++;break;
                    case "Recovered":recovered++;
                    break;  case "Dead":dead++;break;
            }
        }


        int total = people.size();

        int x =Math.max(10, getWidth() - 245 );


        g.setColor(Color.WHITE);

        g.fillRect(x,10,230,125);


        g.setColor(Color.BLACK);

        g.drawRect(x,10,230,125);

        g.drawString("EPIDEMIC STATUS",x + 15,30);


        g.drawString("Total People: " + total,x + 15,50);
        g.drawString(String.format("Healthy: %d (%.1f%%)",healthy,percent(healthy, total)),x + 15,70);

        g.drawString(String.format("Infected: %d (%.1f%%)",infected,percent(infected, total)),x + 15,88);
        g.drawString(String.format("Recovered: %d (%.1f%%)",recovered,percent(recovered, total)),x + 15,106);

        g.drawString(String.format("Dead: %d (%.1f%%)",dead,percent(dead, total)),x + 15,124 );
    }

    // Calculates the percentage of the population in a given state
    private double percent(
            int amount,
            int total) {

        if (total == 0) {
            return 0;
        }
       return amount * 100.0 / total;
    }
}