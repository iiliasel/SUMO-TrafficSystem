# SUMO Traffic System – Java 21 Real-Time Traffic Simulation

A real-time traffic simulation system built with **Java 21** and **SUMO (Simulation of Urban Mobility)** using the official **libtraci** interface.  
This project establishes a communication pipeline between Java and SUMO and lays the foundation for a full GUI-based traffic control system.

---

## 👥 Team Members
This project is developed collaboratively by:  
**Ilias, Yilin, Selim, Alex, Enes**

---

## 1. Project Overview

### 1.1 Introduction
The SUMO Traffic System aims to provide a modular and extensible Java-based control system for SUMO.  
Using **libtraci**, the project enables:

- programmatic SUMO simulation control
- step-by-step & continuous execution
- simulation file loading
- data extraction for vehicles, routes & traffic lights
- future expansion into GUI-based visualization & analytics

The structure and implementation align with the academic **Milestone 1–3 requirements**.

---

### 1.2 Core Features

#### ✔ Milestone 1 (Completed Fundamentals)
- Java 21 project setup
- SUMO/libtraci integration
- Portable SUMO support inside project folder
- Example simulation startup & step execution
- Clean repository structure with `.gitignore`
- Documentation and code demonstration

#### 🚧 Milestone 2 (In Progress)
- Vehicle injection & parameter editing
- Traffic light inspection & manipulation
- Initial GUI prototype (Swing/JavaFX)
- Basic statistics extraction
- Javadoc documentation

#### 🏁 Milestone 3 (Planned)
- Full interactive GUI
- Map visualization
- Data analytics dashboard
- CSV/PDF report export
- Complex scenario testing
- Final project polish and presentation

---

## 2. Technology Stack

| Component | Technology |
|----------|------------|
| Programming Language | **Java 21** |
| Simulation Engine | **SUMO 1.25.0 (portable)** |
| Communication API | **libtraci (Java bindings)** |
| GUI Framework | Swing (JavaFX optional) |
| IDE | IntelliJ IDEA |
| Version Control | Git + GitHub |

---

## 3. Installation & Setup

### 3.1 Prerequisites
- **Java 21** installed
- IntelliJ IDEA
- Git installed
- SUMO **portable ZIP** (recommended: 1.25.0)

---

### 3.2 Clone the Repository

```bash
git clone https://github.com/iiliasel/SUMO-TrafficSystem.git
cd SUMO-TrafficSystem
