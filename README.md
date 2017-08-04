# Aos-Mutual-Exclusion

## Compilation

Run `make deploy nid=NETID` to deploy the project to the UTD servers. Note that no directory named `Aos-Mutual-Exclusion` should be present on these servers, and if you choose to run the project from them, you should do so from a copy of the project located in a directory of some other name (for example, `~/aosme`).

## Scripts

All scripts should be run from their directory of residence.

* `./launcher NETID CONFIG [GREEDY]`: Launches the configuration file specified by CONFIG as greedy or non greedy. CONFIG can either be a file name or a path to a configuration file, but the file must be located in the `config` directory. The greedy argument is optional and defaults to false. For a greedy launch, the argument should be `true`. NETID should be your student NetID.

* `./progress [CSVOUT] [COLUMNS]`: Monitors the running instances (or previously run instances) and outputs relevant statistics upon their termination. The `CSVOUT` argument specifies an output file in which to store CSV-formatted data on the run. The `COLUMNS` argument should be ignored and is only used for scripting purposes.

* `./metalauncher NETID MCONFIG CSVOUT`: Launches, monitors, and collects the outputs of multiple runs based on the configurations specified in the meta-configuration MCONFIG (located in the `mconfig` directory).

* `./status`: Checks for running Java processes on each of the machines specified in the last run configuration file.

* `./cleanup`: Terminates all of the user's Java processes on the machines specified in the last run configuration file. Not generally necessary.

* `./outerr`, `./appouterr`, `./kernouterr`: These display the processes' most recent writes to standard out and standard err for both the kernels and the applications, the applications, and the kernels, respectively.

* `./results`: Identical to `./outerr`, but including log information.
