import org.apache.logging.log4j.Logger;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.TraCILogic;
import org.eclipse.sumo.libtraci.TraCILogicVector;
import org.eclipse.sumo.libtraci.TraCIPhase;

import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Traffic Light Class
 * control and manage Traffic Lights in Sumo Simulation (logic controlling the Traffic Lights)
 */

public class SumoTrafficLights {

    private static final Logger logger = LogManager.getLogger(SumoTrafficLights.class);

    private String id;
    private List<String> controlledLanes;
    private String displayLanes;
    private StringBuilder customStateBuilder;

    // constructor for SumoTrafficLights
    public SumoTrafficLights(String id, List<String> controlledLanes){
        this.id = id;
        this.controlledLanes = controlledLanes;
        String lanesString = controlledLanes.stream().distinct().sorted().collect(Collectors.joining(", "));
        this.displayLanes = "TL " + id + " [" + lanesString + "]";
    }

    //------------------------Getter Methoden--------------------------------

    public List<String> getControlledLanes(){
        return controlledLanes;
    }

    @Override
    public String toString(){
        return displayLanes;
    } // returns displayLanes for Jlist

    public List<String> getPhasesGUI(){
        List<String> phaseList = new ArrayList<>();

        try {
            TraCILogic logic = getFirstLogic();

            if (logic != null){
                List<TraCIPhase> phases = logic.getPhases();

                // Count Phases and add Index Number
                for (int i = 0; i < phases.size(); i++){
                    phaseList.add("Phase " + i);
                }
            }
        }catch (Exception e){
            logger.error("Error loading List with Phases");
        }
        return phaseList;
    } // gives a List of Phases of selceted Traffic Light

    private TraCILogic getFirstLogic(){
        try {
            TraCILogicVector TlPrograms = TrafficLight.getCompleteRedYellowGreenDefinition(id);
            if (TlPrograms.size() > 0){
                return TlPrograms.get(0);
            }
            else{
                logger.info("Selected TL has no Programs");
            }
        } catch (Exception e) {
            logger.error("ERROR loading first Programm of selected TL!");
        }
        return null;
    } // return first Programm of selected Traffic Light

    public String getCustomStateString(){
        return customStateBuilder.toString();
    }

    //------------------------Logic--------------------------

    /**
     * Sends the update Phase to Sumo
     * @param index Number of Phase in the list
     * @param newState new string with the user set State
     * @param duration number of duration in seconds for phase
     */
    public void updatePhase(int index, String newState, double duration){
        try {
            TraCILogic logic = getFirstLogic();
            if (logic == null){
                return;
            }

            List<TraCIPhase> phases = logic.getPhases();

            if (index >= 0 && index < phases.size()){
                TraCIPhase p = phases.get(index);
                p.setState(newState);
                p.setDuration(duration);

                TrafficLight.setCompleteRedYellowGreenDefinition(id, logic);

                TrafficLight.setProgram(id, logic.getProgramID());

                logger.info("Phase " + index + " updated");
            }
        }catch (Exception e){
            logger.error("ERROR updating Phase!");
        }
    }


    /**
     * Sets the RedYellowGreenState String to all Red
     */
    public String allRed(){
        return "r".repeat(TrafficLight.getRedYellowGreenState(id).length());
    }

    /**
     * Sets RedYellowGreenState String to all Yellow
     */
    public String allYellow(){
        return "y".repeat(TrafficLight.getRedYellowGreenState(id).length());
    }

    /**
     * Sets RedYellowGreenSate String to all Green
     */
    public String allGreen(){
        return "g".repeat(TrafficLight.getRedYellowGreenState(id).length());
    }

    /**
     * gets the current State of the Traffic light and initialize it to the customState String
     */
    public void initCustomPhase(){
        try{
            customStateBuilder = new StringBuilder(TrafficLight.getRedYellowGreenState(id));
        } catch (Exception e) {
            logger.error("ERROR initializing Custom Phase!");
        }
    }

    /**
     * sets the customState String to what User has selected
     * @param lane selecetd Edge of TL
     * @param color selected Color
     */
    public void setLaneColor(String lane, char color){
        if (customStateBuilder == null){
            initCustomPhase();
        }
        for (int i = 0; i < controlledLanes.size(); i++){
            if (controlledLanes.get(i).equals(lane) && i < customStateBuilder.length()){
                customStateBuilder.setCharAt(i, color);
            }
        }
    }

    /**
     * adds new Phase for selected TL
     */
    public void addPhase(){
        try{
            TraCILogic logic = getFirstLogic();
            if (logic == null){
                return;
            }

            List<TraCIPhase> phases = logic.getPhases();

            String defaultPhase = TrafficLight.getRedYellowGreenState(id);
            double defaultDuration = TrafficLight.getPhaseDuration(id);

            TraCIPhase newPhase = new TraCIPhase(defaultDuration, defaultPhase);
            phases.add(newPhase);

            TrafficLight.setCompleteRedYellowGreenDefinition(id, logic);
            TrafficLight.setProgram(id, logic.getProgramID());

            logger.info("New Phase added");

        }catch (Exception e){
            logger.error("Error adding new Phase!");
        }
    }

    /**
     * removes selecetd Phase from the Traffic Lights
     * @param index number of selected Phase user wants to remove
     */
    public void removePhase(int index){
        try{
            TraCILogic logic = getFirstLogic();
            if (logic == null){
                return;
            }

            List<TraCIPhase> phases = logic.getPhases();

            if (index >= 0 && index < phases.size()){

                if (phases.size() <= 1){
                    logger.error("Error! Can not remove last remaining phase!");
                    return;
                }

                phases.remove(index);

                TrafficLight.setCompleteRedYellowGreenDefinition(id , logic);
                TrafficLight.setProgram(id, logic.getProgramID());

                logger.info("Phase " + index + " removed");
            }
        }catch (Exception e){
            logger.error("Error removing Phase " + index);
        }
    }

}
