import java.awt.*;
import java.awt.event.*;

public class Main {

    public static void main(String[] args) {
    
        Frame frame = new Frame("Epidemic Spread Simulation");
        SimulationPanel panel = new SimulationPanel();

        frame.setLayout(new BorderLayout());
        frame.add(panel);
        frame.setSize(900, 650);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {

    @Override
    public void windowClosing(WindowEvent e) {
        panel.stopSimulation();
        frame.dispose();
        System.exit(0);}});
        panel.startSimulation();
    }
}