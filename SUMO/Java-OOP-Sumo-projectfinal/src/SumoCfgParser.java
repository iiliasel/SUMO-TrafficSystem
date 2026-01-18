import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.util.List;

/**
 * SUMO Configuration Parser
 * Used to extract information from .sumocfg files.
 */
public class SumoCfgParser {
    public static String parseNetFilePath(String sumocfgPath) throws Exception {
        File cfgFile = new File(sumocfgPath);
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document = saxBuilder.build(cfgFile);
        Element root = document.getRootElement();

        // Look for the <input> tag, which is the parent for file definitions in SUMO
        Element inputElem = root.getChild("input");
        if (inputElem == null) {
            throw new IllegalArgumentException("The sumocfg file is missing the <input> configuration.");
        }

        // Look for the <net-file> tag under <input>
        List<Element> netFileElems = inputElem.getChildren("net-file");
        if (netFileElems.isEmpty()) {
            throw new IllegalArgumentException("No network file (.net.xml) is configured in the sumocfg file.");
        }

        // Get the network file path from the 'value' attribute (supports relative/absolute paths)
        String netFileName = netFileElems.get(0).getAttributeValue("value");

        // Resolve the absolute path based on the location of the .sumocfg file
        File netFile = new File(cfgFile.getParentFile(), netFileName);
        return netFile.getAbsolutePath();
    }
}