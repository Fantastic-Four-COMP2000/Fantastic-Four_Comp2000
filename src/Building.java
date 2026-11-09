public abstract class Building {

    private int x;
    private int y;
    private int width;
    private int height;

    public Building(int x,int y,int width,int height) {

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    // Horizontal Position
    public int getX() {
        return x;
    }
    
    // Vertical Position 
    public int getY() {
        return y;
    }

    // Width
    public int getWidth() {
        return width;
    }
    
    // Height
    public int getHeight() {
        return height;
    }
    
    
    // Checks whether a person is currently inside the building area
    public boolean isPersonInside(Person person) {

        return person.getX() >= x && person.getX() <= x + width && person.getY() >= y && person.getY() <= y + height;
    }
}