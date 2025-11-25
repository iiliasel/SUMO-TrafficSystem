# Project Overviwe

## 1. Document Description
This document covers project background, objectives, core functions, application scenarios, and expected outcomes.

## 2. Background
Nowadays, problems such as traffic congestion and frequent accidents have become increasingly prominent.Intelligent Transportation Systems have become the key to solving such problems.
SUMO can accurately simulate the operation status of urban traffic flow. However, native sumo-gui.exe relies on its built-in GUI, lacking customized data management, visualization.

This project aims to develop a SUMO simulation control system based on Java. By encapsulating SUMO's TraCI interface, construct a GUI interface with visual monitoring, flexible control,
and data statistical analysis functions. Provide efficient simulation tool support for research on traffic planning, signal optimization, and vehicle scheduling.

## 3. Project Objectives
### 3.1 Core Objectives
1. Achieve stable communication between Java and SUMO: Encapsulate communication logic based on the libtraci interface to support basic operations such as configuration file loading, simulation start/stop, and step/continuous advancement.
2. Build an intuitive visual interface: Provide real-time display of road network maps, vehicle trajectory tracking, traffic light status visualization, and support interactive operations such as map zooming and panning.
3. Improve data statistical analysis: Collect core data such as vehicles and traffic lights in real-time during the simulation process, and provide multi-dimensional statistical indicators and data export functions.
4. Ensure system stability and usability: Handle various abnormal scenarios, optimize operation processes, and enable non-professional users to quickly get started.


### 3.2 Phased Objectives
- Milestone 1: Complete the basic framework construction, including project architecture design, libtraci interface encapsulation, simple GUI prototype development, and core document writing.
- Milestone 2: Implement visualization and data statistics functions, including map drawing, real-time data display, data export, and basic analysis functions.
- Milestone 3: Optimize system performance and user experience, fix bugs, add advanced functions (such as scenario comparison and signal optimization simulation), and complete system testing and delivery.

## 4. Core Functions
### 4.1 Functions
- **Live SUMO Integration**:
• Use the TraaS API to connect to a running SUMO simulation. 
• Step through the simulation in real time. 
• Inject vehicles, read telemetry, and control traffic lights programmatically. 
- **Interactive Map Visualization**: 
• Render the road network. 
• Display moving vehicles with color-coded icons. 
• Show traffic lights with current phase indicators. 
• Support zooming, panning, and camera rotation. 
• Show subsets of vehicles according to their properties (filtered by e.g. color, speed, 
or location) 
- **Vehicle Injection & Control**:
• Allow users to create vehicles on specific edges via GUI. 
• Support batch injection for stress testing. 
• Enable control over vehicle parameters (speed, color, route). 
- **Traffic Light Management**: 
• Display traffic light states and phase durations. 
• Enable manual phase switching via GUI. 
• Allow users to adjust phase durations and observe effects on traffic flow. 
- **Statistics & Analytics**:
• Track metrics such as: Average speed,Vehicle density per edge,Congestion hotspots,Travel time distribution 
• Display charts and summaries in real time. 
- **Exportable Reports**:
• Save simulation statistics to CSV for external analysis. 
• Generate PDF summaries with charts, metrics, and timestamps. 
• Include filters (e.g. only red cars, only congested edges) in exports.

### 4.2 Exception Handling Functions
- **Error Prompt**: Provides clear pop-up prompts and log records for abnormal scenarios such as configuration file errors, SUMO path errors, and port occupation.
- **Status Recovery**: When SUMO shuts down unexpectedly, the system automatically detects the connection interruption and resets the interface status to ensure data security.

## 5. Additional Recommended Features
- **Stress Testing Tools**:
• Simulate heavy traffic on selected edges. 
• Observe system behavior under load. 
• Compare static vs adaptive traffic light strategies. 
- **Adaptive Traffic Control**:
• Allow users to experiment with traffic light timing to improve flow. 
• Provide feedback on performance metrics after adjustments. 
• Optionally integrate simple rule-based adaptation. 
- **3D Rendering (Optional)**:
• Use JavaFX 3D to render intersections or vehicles from different angles. 
• Animate camera movement or simulate drone views.

## 6. Expected Outcomes
- A runnable Java-SUMO simulation control system, including a complete GUI interface and background logic.
- A complete set of project documents, including architecture design, class design, and user manual.
- An open-source Git repository, including project source code, documents, and version control records.

















