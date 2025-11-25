# java-sumo-simulation-system
Java-based SUMO Traffic Simulation Control System with GUI, supporting simulation control, data visualization and statistical analysis.

## 1.Project Overview
### 1.1 Introduction
This project aims to develop a user-friendly contrpl system for SUMO using Java. It uses the libtraci interface to realize stable communication between Java and SUMO, and provides a different GUI to simplify simulation operations, visualize status and analyze simulation data.

### 1.2 Core Features
- **Live SUMO Integration**: Load SUMO configuration, start/stop simulation, step-by-step/continuous simulation, speed adjustment.
- **Interactive Map Visualization**: Real-time display of road network, vehicle trajectory, traffic light status, with map zoom/pan functions.
- **Vehicle Injection & Control**: Allow users to create vehicles on specific edges via GUI; Support batch injection for stress testing; Enable control over vehicle parameters (speed, color, route).
- **Traffic Light Management**: Display traffic light states and phase durations; Enable manual phase switching via GUI; Allow users to adjust phase durations and observe effects on traffic flow.
- **Statistics & Analytics**:Real-time statistics of vehicle/traffic light data, data export (CSV), detailed data query.
- **Exportable Reports**: Save simulation statistics to CSV for external analysis; Generate PDF summaries; Include filters.
- **Error Handling**: Comprehensive exception prompts for configuration errors, connection failures, etc.

## 2. Technology Stack
- Programming Language: Java,JDK 1.8+
- GUI Framework: Swing
- Simulation Engine: SUMO
- Communication Interface: libtraci(Match SUMO Version)

## 3. Installation & Setup
### 3.1 Prerequisites
1. Install JDK 1.8+, configure environment variables.
2. Configure a suitable Java editor,such as IntelliJ IDEA or Eclipse.
3. Install SUMO 1.10.0+, add Sumo to system environment variables.
4. Install Git 2.30.0+.

### 3.2 Repository Clone
```bash
git clone https://github.com/jyl-cell/java-sumo-simulation-system.git
cd java-sumo-simulation-system
```

### 3.3 Project Import
1. Open IDE(IntelliJ IDEA/Eclipse), import the project as a Java project.
2. Add libtraci-1.--.0.jar and libtraci-1.--.0-resources.jar to the project's build path(located in sumo-1.--.0\bin).
3. Add libtracicpp.lib, libtracics.dll, libtracijni.dll to the build path(located in sumo-1.--.0\bin).

## 4. Usage Guide
### 4.1 Basic Operation
1. **Load Configuration**:




## 5. Repository Structure
```
java-sumo-simulation-system/
├── src/                     # Source code directory
│   ├── main/
│   │   ├── java/
│   │   │   ├── com/
│   │   │   │   ├── sumo/
│   │   │   │   │   ├── connector/  # libtraci encapsulation (SumoConnector, etc.)
│   │   │   │   │   ├── manager/    # Data managers (VehicleManager, etc.)
│   │   │   │   │   ├── service/    # Business logic (SimulationService, etc.)
│   │   │   │   │   └── gui/        # GUI components (frames, panels, etc.)
│   │   └── resources/              # Resource files (icons, config templates, etc.)
│   └── test/                       # Test code directory
├── docs/                         # Project documents (architecture, class design, etc.)
├── .gitignore                    # Git ignore file
├── LICENSE                       # License file
└── README.md                     # Project introduction (this file)
```









