public class Dead extends Person {
     
    // Creates a dead person at their current position with no movement
    public Dead(int id,String name,int age,float x,float y) {
    super(id, name, age, x, y, 0, 0);
    }
    
    // Returns the current healthy status of this person
    @Override
    public String getStatus() {
        return "Dead";
    }
}