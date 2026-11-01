import java.awt.*;
import java.awt.event.*;

public class Main {

    public static void main(String[] args) {

        // Default: age-based infection and sequential statistics.
        InfectionStrategy strategy = new AgeBasedInfectionStrategy();

        boolean fixedRule = false;
        boolean parallelStatistics = false;

        // Read the optional settings from the command line.
        for (String argument : args) {
            if ("fixed".equalsIgnoreCase(argument)) {
                fixedRule = true;
            } else if ("parallel".equalsIgnoreCase(argument)) {
                parallelStatistics = true;
            } else {
                System.out.println(
                        "Usage: java -cp EpidemicSimulationCode Main "
                                + "[fixed] [parallel]"
                );
                return;
            }
        }

        if (fixedRule) {
            /*
             * Alternative Strategy implemented with a lambda.
             * Each eligible close contact uses the virus's spread chance,
             * without applying age or repeated-exposure rules.
             */
            strategy = (person, virus) ->
                    Math.random() * 100 < virus.getSpreadPercentage();
        }

        // These values are captured by the GUI creation lambda below.
        final InfectionStrategy selectedStrategy = strategy;
        final boolean selectedParallelMode = parallelStatistics;

        System.out.println(
                "Infection rule: "
                        + (fixedRule ? "Fixed chance" : "Age based")
        );

        System.out.println(
                "Statistics mode: "
                        + (parallelStatistics ? "Parallel" : "Sequential")
        );

        // Create and show AWT components on the event dispatch thread.
        EventQueue.invokeLater(() -> {
            Frame frame = new Frame("Epidemic Spread Simulation");

            SimulationPanel panel = new SimulationPanel(
                    selectedStrategy,
                    selectedParallelMode
            );

            frame.setLayout(new BorderLayout());
            frame.add(panel, BorderLayout.CENTER);
            frame.setSize(900, 650);

            // Stop the simulation when the window is closed.
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    panel.stopSimulation();
                    frame.dispose();
                    System.exit(0);
                }
            });

            frame.setVisible(true);
            panel.startSimulation();
        });
    }
}