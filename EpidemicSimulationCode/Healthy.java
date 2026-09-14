public class Healthy extends Person {
    
    // Tracks how many exposures this person has had due to a close contact
    private int exposureCount = 0;
    
    // Indicates whether this healthy person is immune to reinfection
    private boolean immune = false;
    
    // Creates a new healthy person
    public Healthy(String name,int age,float x,float y,float speedX,float speedY) {
        super(name, age, x, y, speedX, speedY);
    }
    
    // Creates a healthy person while preserving their existing ID and immunity
    public Healthy(int id,String name,int age,float x,float y,float speedX,float speedY,boolean immune) {

        super(id, name, age, x, y, speedX, speedY);
        this.immune = immune;
    }

    // Another constructor for a healthy person
    public Healthy(String name, int age, float x, float y, float speedX, float speedY, boolean immune) {

        super(name, age, x, y, speedX, speedY);
        this.immune = immune;
    }

    // Records another exposure to an infected person
    public void addExposure() {
        exposureCount++;
    }

    public int getExposureCount() {
        return exposureCount;
    }

    public boolean isImmune() {
        return immune;
    }

    @Override
    public String getStatus() {
        return "Healthy";
    }
}