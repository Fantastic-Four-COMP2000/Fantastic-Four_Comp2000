// Factory Method pattern: centralizes how a Person object is created for
// each state transition (newly infected, recovered, vaccinated healthy,
// dead), instead of every caller hand-building the right subclass with
// the same copied fields.
public class PersonCreator {

    // Brand-new infected person (used when the simulation starts)
    public static Infected createInfected(String name, int age, Virus virus,
            float x, float y, float speedX, float speedY) {
        return new Infected(name, age, virus, x, y, speedX, speedY);
    }

    // A healthy person who caught the virus — identity/position/speed preserved
    public static Infected createInfected(Person from, Virus virus) {
        return new Infected(from.getId(), from.getName(), from.getAge(), virus,
                from.getX(), from.getY(), from.getSpeedX(), from.getSpeedY());
    }

    // Brand-new healthy person (used when the simulation starts)
    public static Healthy createHealthy(String name, int age,
            float x, float y, float speedX, float speedY, boolean vaccinated) {
        return new Healthy(name, age, x, y, speedX, speedY, vaccinated);
    }

    // A person becoming healthy again (from Recovered) — identity preserved
    public static Healthy createHealthy(Person from, boolean immune) {
        return new Healthy(from.getId(), from.getName(), from.getAge(),
                from.getX(), from.getY(), from.getSpeedX(), from.getSpeedY(), immune);
    }

    // An infected person who has recovered
    public static Recovered createRecovered(Person from) {
        return new Recovered(from.getId(), from.getName(), from.getAge(),
                from.getX(), from.getY(), from.getSpeedX(), from.getSpeedY());
    }

    // An infected person who did not survive
    public static Dead createDead(Person from) {
        return new Dead(from.getId(), from.getName(), from.getAge(), from.getX(), from.getY());
    }
}