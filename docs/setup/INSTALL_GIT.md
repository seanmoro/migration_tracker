# Installing Git on Remote Server

## Ubuntu/Debian

```bash
sudo apt-get update
sudo apt-get install -y git
```

## CentOS/RHEL

```bash
sudo yum install -y git
```

## Verify Installation

```bash
git --version
```

Then proceed with the deployment:

```bash
export APP_DIR="/home/seans/new_tracker"
git clone https://github.com/seanmoro/migration_tracker.git $APP_DIR
cd $APP_DIR
./DEPLOY_TO_REMOTE.sh
```
