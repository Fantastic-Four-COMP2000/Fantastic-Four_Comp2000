public class Healthy extends Person {
    
    private int exposureCount = 0;
    private boolean immune = false;
    private long hospitalEntryTime = -1;
    
    public Healthy(String name,int age,float x,float y,float speedX,float speedY) {
        super(name, age, x, y, speedX, speedY);
    }
    
    public Healthy(int id,String name,int age,float x,float y,float speedX,float speedY,boolean immune) {
        super(id, name, age, x, y, speedX, speedY);
        this.immune = immune;
    }

    public Healthy(String name, int age, float x, float y, float speedX, float speedY, boolean immune) {
        super(name, age, x, y, speedX, speedY);
        this.immune = immune;
    }

    public void addExposure() {
        exposureCount++;
    }

    public int getExposureCount() {
        return exposureCount;
    }

    public boolean isImmune() {
        return immune;
    }


    public void setImmune(boolean immune) {
        this.immune = immune;
    }

    public long getHospitalEntryTime() {
        return hospitalEntryTime;
    }

    public void setHospitalEntryTime(long time) {
        hospitalEntryTime = time;
    }

    @Override
    public String getStatus() {
        return "Healthy";
    }
}