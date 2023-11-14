# How to

## First time
install nix
``` bash
curl --proto '=https' --tlsv1.2 -sSf -L https://install.determinate.systems/nix | sh -s -- install
```

install direnv
```
nix profile install nixpkgs#direnv
```

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
