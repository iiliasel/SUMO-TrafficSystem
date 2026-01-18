import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartUtilities;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.util.PublicCloneable;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Generates a PDF report for the SUMO simulation containing
 * textual statistics and a graphical chart visualization.
 */

public class PdfReportExporter {


    /**
     * Exports the current simulation data into a formatted PDF report
     * and lets the user choose the save location.
     */
    public void exportPDF(JFrame parent, SimulationData data) throws Exception {

        // 1. Generate timestamp and default file name
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String defaultName = "SUMO_Report_" + timestamp + ".pdf";

        // 2. Let the user choose where to save the PDF file
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save PDF Report");
        chooser.setSelectedFile(new File(defaultName));
        int result = chooser.showSaveDialog(parent);

        if (result != JFileChooser.APPROVE_OPTION) {
            return; // User cancelled the export
        }


        File file = chooser.getSelectedFile();

        // Ensure the file has a .pdf extension
        if (!file.getName().endsWith(".pdf")) {
            file = new File(file.getAbsolutePath() + ".pdf");
        }

        // 3. Create PDF document and add an A4 page
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        // Content stream used to write text and images to the PDF page
        PDPageContentStream content = new PDPageContentStream(doc, page);

        // 4. Write the PDF title
        content.beginText();
        content.setFont(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                20
        );
        content.newLineAtOffset(50, 770);
        content.showText("SUMO Simulation Report");
        content.endText();

        // Write basic simulation statistics
        content.beginText();
        content.setFont(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                12
        );
        content.newLineAtOffset(50, 740);

        // Write basic simulation statistics
        content.showText("Timestamp: " + java.time.LocalDateTime.now());
        content.newLineAtOffset(0, -20);
        content.showText("Total Vehicles: " + data.getVehicleTotal());
        content.newLineAtOffset(0, -15);
        content.showText("Running Vehicles: " + data.getVehicleRunning());
        content.newLineAtOffset(0, -15);
        content.showText("Congested Vehicles: " + data.getVehicleCongested());
        content.newLineAtOffset(0, -15);
        content.showText("Traffic Lights: " + data.getTlTotal());
        content.newLineAtOffset(0, -15);
        content.showText("Avg Speed: " + String.format("%.2f", data.getAvgSpeed()) + " km/h");
        content.newLineAtOffset(0, -15);
        content.showText("Efficiency: " + String.format("%.2f", data.getTrafficEfficiency()) + " %");

        content.endText();

        // 5. Create a chart using JFreeChart
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(data.getVehicleTotal(), "Vehicles", "Total");
        dataset.addValue(data.getVehicleRunning(), "Vehicles", "Running");
        dataset.addValue(data.getVehicleCongested(), "Vehicles", "Congested");

        // Generate the bar chart visualization
        JFreeChart chart = ChartFactory.createBarChart(
                "Vehicle Overview",
                "Category",
                "Count",
                dataset
        );

        BufferedImage chartImage = chart.createBufferedImage(400, 300);

        // Save the chart temporarily as a PNG file
        File chartFile = new File("chart_temp.png");
        ChartUtilities.saveChartAsPNG(chartFile, chart, 400, 300);

        // 6. Insert the chart image into the PDF
        PDImageXObject pdImage = PDImageXObject.createFromFile("chart_temp.png", doc);
        content.drawImage(pdImage, 100, 250, 400, 300);

        content.close();

        // 7. Save and close the PDF document
        doc.save(file);
        doc.close();

        // 8. Delete the temporary chart image file
        chartFile.delete();

        // Notify the user that the export was successful
        JOptionPane.showMessageDialog(parent,
                "PDF exported to:\n" + file.getAbsolutePath(),
                "Export Successful",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
