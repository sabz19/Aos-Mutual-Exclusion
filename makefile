SHELL=/bin/bash -O extglob -c
ROOTDIR=~/Aos-Mutual-Exclusion

Aos-Mutual-Exclusion:
	rm -rf bin
	mkdir bin
	javac -d bin -sourcepath src $(shell find src -name "*.java")

deploy:
	chmod +x appouterr cleanup kernouterr launcher metalauncher outerr progress results status
	ssh ${nid}@dc01.utdallas.edu "mkdir -p ${ROOTDIR}; rm -rf ${ROOTDIR}/*"
	scp -r !(*.git|bin|.configtemp|.netidtemp) ${nid}@dc01.utdallas.edu:${ROOTDIR}
	ssh ${nid}@dc01.utdallas.edu "cd ${ROOTDIR}; make Aos-Mutual-Exclusion"
