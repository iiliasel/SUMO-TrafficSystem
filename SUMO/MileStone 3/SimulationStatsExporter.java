import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class SimulationStatsExporter {

    public static String exportToCSV(SimulationData data) throws  IOException {

        File dir = new File("exports");
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            System.out.println("Export Erstellt?" + ok);
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filePath = "exports/simulation_stats_" + timestamp + ".csv";

        FileWriter writer = new FileWriter(filePath);

        writer.write("Metric, Value\n");
        writer.write("Total Vehicles," + data.getVehicleTotal() + "\n");
        writer.write("Running Vehicles," + data.getVehicleRunning() + "\n");
        writer.write("Congested Vehicles," + data.getVehicleCongested() + "\n");

        writer.write("Traffic Lights Total," + data.getTlTotal() + "\n");
        writer.write("Red Lights," + data.getTlRed() + "\n");
        writer.write("Green Lights," + data.getTlGreen() + "\n");
        writer.write("Yellow lights," + data.getTlYellow() + "\n");

        writer.write("Total Steps," + data.getTotalSteps() + "\n");
        writer.write("Average Speed (km/h)," + data.getAvgSpeed() + "\n");
        writer.write("Traffic Efficiency (%)," + data.getTrafficEfficiency() + "\n");
        writer.write("Simulation Time," + data.getSimulationTime() +"\n");

        writer.close();

        return filePath;
    }



}
