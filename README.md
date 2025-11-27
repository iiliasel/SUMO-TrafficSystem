# 1. Project Overview

## 1.1 Purpose
The goal of this project is to build a Java-based control and visualization layer for the SUMO traffic simulator using the libtraci interface.  
Milestone 1 focuses on establishing the project foundation through architecture design, component planning, and the demonstration of a working SUMO integration.

## 1.2 System Description
The system provides a communication pipeline between Java 21 and SUMO 1.25.0 via libtraci.  
This enables the application to load SUMO configurations, start and control simulations programmatically, inspect traffic entities, and later visualize them through a GUI.

Milestone 1 establishes:
- Portable SUMO integration  
- Working Java → libtraci → SUMO communication  
- Basic simulation control (load config, step execution)  
- Architecture design for future GUI, logic, and analytics modules  
- Mockups and class structure for upcoming milestones  
- Repository setup and technical documentation  

---

# 2. Team Roles

| Member | Responsibility |
|--------|----------------|
| Ilias | GitHub management, README, documentation, project overview |
| Selim | Java development, SUMO integration, connection demo |
| Yilin | Java development, SUMO integration, connection demo |
| Enes | GUI mockups (map, control panel, dashboard) |
| Alex | Architecture design, class design, structural planning |

All team members contribute equally to discussions, testing, refinement, and overall project progress.

---

# 3. Time Plan (Feature → Schedule)

---

## Milestone 1 – Foundation (Due: 27.11.2025)  
**Status: COMPLETED ✔️**

### Feature / Task | Status
- GitHub repository setup, .gitignore, structure — ✔️ Done  
- Java 21 project configuration — ✔️ Done  
- SUMO portable integration — ✔️ Done  
- libtraci integration + demo (run .sumocfg, step simulation) — ✔️ Done  
- Technology stack summary — ✔️ Done  
- Project overview documentation — ✔️ Done  
- Architecture diagram — ✔️ Done  
- Class design (Vehicle, TrafficLight, Controller) — ✔️ Done  
- GUI mockups (Map, Control Panel, Dashboard) — ✔️ Done  
- Time plan — ✔️ Done  
- Team roles — ✔️ Done  

---

## Milestone 2 – Functional Prototype (Due: 14.12.2025)

### Feature / Task | Status
- Vehicle spawning via libtraci — ☐ To Do  
- Traffic light inspection & control — ☐ To Do  
- Basic GUI prototype (Swing/JavaFX) — ☐ To Do  
- Real-time simulation controller — ☐ To Do  
- Statistics extraction — ☐ To Do  
- Javadoc documentation — ☐ To Do  
- User guide draft — ☐ To Do  
- Stress test scenario — ☐ To Do  

---

## Milestone 3 – Final Application (Due: 18.01.2026)

### Feature / Task | Status
- Full interactive GUI with visualization — ☐ To Do  
- Vehicle grouping & filtering — ☐ To Do  
- Traffic light adaptation logic — ☐ To Do  
- Export tools (CSV, PDF) — ☐ To Do  
- Final documentation & project cleanup — ☐ To Do  
- Final testing & presentation preparation — ☐ To Do  
