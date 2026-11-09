public abstract class Person {

    // Used to automatically assign an ID to each new person.
    private static int nextId = 1;

    private int id;
    private String name;
    private int age;

    private float x;
    private float y;
    private float speedX;
    private float speedY;

    // Creates a new person and automatically assigns an ID.
    public Person(
            String name,
            int age,
            float x,
            float y,
            float speedX,
            float speedY) {

        // Validate before assigning an ID or storing the data.
        validateDetails(name, age);

        this.id = nextId++;
        this.name = name.trim();
        this.age = age;

        this.x = x;
        this.y = y;
        this.speedX = speedX;
        this.speedY = speedY;
    }

    // Creates a person while preserving an existing ID.
    public Person(
            int id,
            String name,
            int age,
            float x,
            float y,
            float speedX,
            float speedY) {

        validateDetails(name, age);

        this.id = id;
        this.name = name.trim();
        this.age = age;

        this.x = x;
        this.y = y;
        this.speedX = speedX;
        this.speedY = speedY;
    }

    // Both constructors share the same name and age rules.
    private static void validateDetails(String name, int age) {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Person name cannot be empty."
            );
        }

        if (age < 0 || age > 120) {
            throw new IllegalArgumentException(
                    "Age must be between 0 and 120."
            );
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getSpeedX() {
        return speedX;
    }

    public float getSpeedY() {
        return speedY;
    }

    // Moves the person and reverses direction at a boundary.
    public void move(int width, int height) {

        x += speedX;
        y += speedY;

        if (x < 0 || x > width - 15) {
            speedX *= -1;
        }

        if (y < 150 || y > height - 15) {
            speedY *= -1;
        }
    }

    public abstract String getStatus();

    @Override
    public String toString() {
        return "ID: " + id
                + ", Name: " + name
                + ", Age: " + age
                + ", Status: " + getStatus();
    }
}