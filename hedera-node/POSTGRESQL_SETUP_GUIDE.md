# PostgreSQL Setup Guide

This document describes the standard Swirlds configuration for the PostgreSQL local development environment.

## PostgreSQL Setup for Local Development
1.	Download and Install Docker CE: [Windows](https://hub.docker.com/editions/community/docker-ce-desktop-windows) or [MacOS X](https://hub.docker.com/editions/community/docker-ce-desktop-mac)
a.	If prompted, choose to use Linux Containers during installation
b.	On Windows, you may be forced to log off after the install completes.
c.	On Windows, if Hyper-V and Containers features are disabled you will see the prompt below. Save your work, press Ok, and wait for your computer to restart.
2.	Create a local folder to use with PostgreSQL:   [MacOS/Linux Only]
```
# MacOS / Linux
mkdir -p ~/Docker/Volumes/PostgreSQL/swirlds-fcfs
```
3.	Execute the following docker commands from the CLI:
```
# MacOS / Linux
docker run --name postgres -d -p 5432:5432 \
-v ~/Docker/Volumes/PostgreSQL/swirlds-fcfs:/var/lib/postgresql/data \
--env POSTGRES_PASSWORD=password --env POSTGRES_USER=swirlds \
--env POSTGRES_DB=fcfs \
--env PGDATA=/var/lib/postgresql/data/pgdata \
postgres:10.9-alpine

# Windows
cd “%USERPROFILE%” 
docker run --name postgres -d -p 5432:5432 ^
--env POSTGRES_PASSWORD=password --env POSTGRES_USER=swirlds ^
--env POSTGRES_DB=fcfs ^
--env PGDATA=/var/lib/postgresql/data/pgdata ^
postgres:10.9-alpine
```
a.	On Windows, you may be asked to authorize the drive sharing as shown below. Press the “Share It” button to allow access.
4.	Copy the [PostgreSQL Configuration file](postgresql.conf) into the appropriate folder (also available in the folder beside this document):    [MacOS/Linux Only]
```
# MacOS / Linux
~/Docker/Volumes/PostgreSQL/swirlds-fcfs/pgdata

```
5.	Control your PostgresSQL container with the following commands:
```
# Start Postgres
docker start postgres

# Stop Postgres
docker stop postgres

# List Running Containers
docker ps

# List all Containers
docker ps -a

```
