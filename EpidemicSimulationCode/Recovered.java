// Represents a person who has recovered from the virus
public class Recovered extends Person {

    private long recoveredSince;

    public Recovered(
            int id,
            String name,
            int age,
            float x,
            float y,
            float speedX,
            float speedY) {

        super(id, name, age, x, y, speedX, speedY);

        recoveredSince = System.currentTimeMillis();
    }

    public long getRecoveredSince() {
        return recoveredSince;
    }

    @Override
    public String getStatus() {
        return "Recovered";
    }
}