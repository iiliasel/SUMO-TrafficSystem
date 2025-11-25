SUMO Traffic System – Java 21 Real-Time Traffic Simulation

A real-time traffic simulation system built with Java 21 and SUMO (Simulation of Urban Mobility) using the official libtraci interface.
This project provides a foundation for controlling SUMO simulations through Java, visualizing traffic behavior, and preparing a full GUI-based system for later milestones.

👥 Team Members

This project is developed collaboratively by:
Ilias, Yilin, Selim, Alex, Enes

1. Project Overview
   1.1 Introduction

The SUMO Traffic System aims to provide a modular and extensible Java-based control system for SUMO.
Using libtraci, the project establishes a stable communication link between Java and SUMO, enabling:

programmatic simulation control

extraction and monitoring of vehicle and traffic light states

support for future GUI development

portable execution without requiring system-wide SUMO installation

This project is structured to align with the requirements of Milestone 1–3 from the course assignment .

1.2 Core Features (Current & Planned)
✔ Milestone 1 (Completed Foundations)

Java 21 project setup

GitHub repository with structure & documentation

SUMO integration demo (start, step, close simulation)

Portable SUMO support (project-local installation)

Architecture & technology overview

Basic code structure for future vehicle/light controllers

🚧 Milestone 2 (To Be Implemented)

Vehicle injection & manipulation

Traffic light control

Basic map visualization (GUI)

Stress-test scenario

Inline code documentation (Javadoc)

User guide draft

🏁 Milestone 3 (Final Stage)

Full GUI with map, controls, statistics

Route/vehicle filtering & grouping

Traffic light adaptation logic

Exportable reports (CSV / PDF)

Final documentation + retrospective

Demonstration-ready application

2. Technology Stack
   Component	Technology
   Programming Language	Java 21 (LTS)
   Communication Layer	libtraci (SUMO 1.25.0)
   Simulation Engine	SUMO (portable ZIP)
   GUI Framework	Swing (planned), JavaFX optional
   IDE	IntelliJ IDEA Ultimate/Community
   Version Control	Git + GitHub
3. Installation & Setup
   3.1 Prerequisites

Java 21 installed

Git installed

IntelliJ IDEA installed

SUMO portable ZIP (1.25.0) — no system installation required

Extract SUMO into:

SUMO-TrafficSystem/sumo/sumo-1.25.0/

3.2 Clone the Repository
git clone https://github.com/iiliasel/SUMO-TrafficSystem.git
cd SUMO-TrafficSystem

3.3 Add SUMO (Portable)

Expected structure:

SUMO-TrafficSystem/
sumo/
sumo-1.25.0/
bin/
tools/
data/


SUMO must NOT be uploaded to GitHub — automatically ignored via .gitignore.

3.4 IntelliJ Setup

Open the project in IntelliJ

Add the libtraci JAR:

libs/libtraci-1.25.0.jar


→ Scope: Compile

Add VM Options under Run Configuration:

-Djava.library.path="./sumo/sumo-1.25.0/bin"


Ensure Java 21 SDK is selected under Project Structure.

4. Usage Guide
   4.1 Example: Start SUMO & Run 5 Steps

(This demo is required in Milestone 1: “SUMO Connection Demo” )

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;

public class APTTest {
public static void main(String[] args) {
Simulation.preloadLibraries();

        // Portable base directory
        String base = System.getProperty("user.dir");

        String sumoExe = base + "/sumo/sumo-1.25.0/bin/sumo-gui.exe";
        String cfgFile = base + "/sumo/sumo-1.25.0/tools/game/rail/test.sumocfg";

        Simulation.start(new StringVector(new String[]{
                sumoExe,
                "-c",
                cfgFile
        }));

        // Run 5 simulation steps
        for (int i = 0; i < 5; i++) {
            Simulation.step();
        }

        Simulation.close();
    }
}

4.2 Run Configuration (Important)

VM Options:

-Djava.library.path="./sumo/sumo-1.25.0/bin"


Working Directory:

$PROJECT_DIR$

5. Directory Structure
   SUMO-TrafficSystem/
   │
   ├── src/                    # Java source code
   │   └── APTTest.java
   │
   ├── libs/                   # libtraci JARs (not tracked in Git)
   │
   ├── sumo/                   # Local SUMO installation (ignored in repo)
   │   └── sumo-1.25.0/
   │       ├── bin/
   │       ├── tools/
   │       └── ...
   │
   └── README.md

6. Future Work

Planned features aligned with Milestones 2 & 3:

Interactive GUI (map, controls, dashboard)

Vehicle control panel

Traffic light adaptation logic

CSV/PDF export

Performance analytics

Stress-test scenarios

7. Contributors

This repository is collaboratively developed by:

Ilias, Yilin, Selim, Alex, Enes

8. License

Educational use only.