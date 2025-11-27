import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;


public class APTTest {
    public static void main(String[] args) {
        Simulation.preloadLibraries();
        Simulation.start(new StringVector(new String[] {"D:\\SUMO\\sumo-1.25.0\\bin\\sumo-gui.exe", "-c", "D:\\SUMO\\demo_practice\\exercise.sumocfg"}));
        for (int i = 0; i < 5; i++) {
            Simulation.step();
        }
        Simulation.close();
    }
}
