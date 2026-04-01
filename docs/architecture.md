# HeiFam Architecture

## Overview
Balancify is an automatic team balancing and ELO-based league management system for StarCraft 3v3 communities.

## Core Domain
- **Group**: Represents a community or league group
- **Player**: Individual players with MMR ratings
- **Match**: A 3v3 game instance
- **Match Participant**: Links players to matches with their team assignments
- **MMR History**: Tracks changes in player MMR over time

## Architecture
- **Frontend**: Next.js with TypeScript and Tailwind CSS
- **Backend**: Spring Boot with Gradle and Java 21
- **Database**: PostgreSQL
- **Local Development**: Docker Compose for PostgreSQL

## Tech Stack
- Frontend: Next.js, TypeScript, Tailwind CSS
- Backend: Spring Boot, Gradle, Java 21
- Database: PostgreSQL
- Containerization: Docker Compose
