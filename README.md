# How to

## First time
```bash
cp .env-sample .env
vi .env # edit the file
dbmate create
```
## To run migrations
```bash
dbmate up
```

## New migration
```bash
dbmate new <migration_name>
```

## To run the server
```bash
sbt coreJVM/run
```