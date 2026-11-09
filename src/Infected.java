public class Infected extends Person {

    private Virus virus;
    private long infectedSince;
    private long tentEntryTime = -1;
    private long hospitalEntryTime = -1;

     // Creates a newly infected person
    public Infected(
            String name,
            int age,
            Virus virus,
            float x,
            float y,
            float speedX,
            float speedY) {

        super(name, age, x, y, speedX, speedY);
        this.virus = virus;
        infectedSince = System.currentTimeMillis();
    }

    // Creates an infected person while preserving their existing ID
    public Infected(
            int id,
            String name,
            int age,
            Virus virus,
            float x,
            float y,
            float speedX,
            float speedY) {

        super(id, name, age, x, y, speedX, speedY);
        this.virus = virus;
        infectedSince = System.currentTimeMillis();
    }

    // Returns the virus affecting this person
    public Virus getVirus() {
        return virus;
    }

     // Returns the time when this person became infected
    public long getInfectedSince() {
        return infectedSince;
    }

    // Returns the time this person entered a recovery tent
    public long getTentEntryTime() {
        return tentEntryTime;
    }

    // Updates the recovery tent entry time
    public void setTentEntryTime(long time) {
        tentEntryTime = time;
    }

     // Returns the time this person entered the hospital
    public long getHospitalEntryTime() {
        return hospitalEntryTime;
    }

    // Updates the hospital entry time
    public void setHospitalEntryTime(long time) {
        hospitalEntryTime = time;
    }

    // Returns the current health status of this person
    @Override
    public String getStatus() {
        return "Infected";
    }
}