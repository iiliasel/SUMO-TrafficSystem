import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

public class APTTest {
    public static void main(String[] args) {
        // Lade native DLLs
        Simulation.preloadLibraries();

        // Starte SUMO mit den NEUEN Pfaden
        Simulation.start(new StringVector(new String[]{
                "C:\\Users\\ilias\\OneDrive\\Desktop\\SUMO\\sumo-win64-1.25.0\\sumo-1.25.0\\bin\\sumo-gui.exe",
                "-c",
                "C:\\Users\\ilias\\OneDrive\\Desktop\\SUMO\\sumo-win64-1.25.0\\sumo-1.25.0\\tools\\game\\rail\\test.sumocfg"
        }));

        // 5 Schritte laufen lassen
        for (int i = 0; i < 5; i++) {
            Simulation.step();
        }

        // Verbingung beenden
        Simulation.close();



        //Kommetar von Enes
    }
}
