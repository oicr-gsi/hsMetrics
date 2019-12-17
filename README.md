# hsMetrics

HSMetrics 2.0

## Overview

## Dependencies

* [picard 2.21.2](https://broadinstitute.github.io/picard/)


## Usage

### Cromwell
```
java -jar cromwell.jar run hsMetrics.wdl --inputs inputs.json
```

### Inputs

#### Required workflow parameters:
Parameter|Value|Description
---|---|---
`inputBam`|File|
`baitBed`|File|
`targetBed`|File|


#### Optional workflow parameters:
Parameter|Value|Default|Description
---|---|---|---
`outputFileNamePrefix`|String|basename(inputBam,'.bam')|


#### Optional task parameters:
Parameter|Value|Default|Description
---|---|---|---
`bedToTargetIntervals.refDict`|String|"$HG19_ROOT/hg19_random.dict"|
`bedToTargetIntervals.jobMemory`|Int|16|
`bedToTargetIntervals.modules`|String|"picard/2.21.2 hg19/p13"|
`bedToTargetIntervals.timeout`|Int|1|
`bedToBaitIntervals.refDict`|String|"$HG19_ROOT/hg19_random.dict"|
`bedToBaitIntervals.jobMemory`|Int|16|
`bedToBaitIntervals.modules`|String|"picard/2.21.2 hg19/p13"|
`bedToBaitIntervals.timeout`|Int|1|
`collectHSmetrics.refFasta`|String|"$HG19_ROOT/hg19_random.fa"|Path to fasta reference file
`collectHSmetrics.metricTag`|String|"HS"|Extension for metrics file
`collectHSmetrics.filter`|String|"LENIENT"|Settings for picard filter
`collectHSmetrics.jobMemory`|Int|18|Memory allocated to job
`collectHSmetrics.coverageCap`|Int|500|Parameter to set a max coverage limit for Theoretical Sensitivity calculations
`collectHSmetrics.modules`|String|"picard/2.21.2 hg19/p13"|Names and versions of modules needed
`collectHSmetrics.timeout`|Int|5|Maximum amount of time (in hours) the task can run for.


### Outputs

Output | Type | Description
---|---|---
`outputHSMetrics`|File|None


## Niassa + Cromwell

This WDL workflow is wrapped in a Niassa workflow (https://github.com/oicr-gsi/pipedev/tree/master/pipedev-niassa-cromwell-workflow) so that it can used with the Niassa metadata tracking system (https://github.com/oicr-gsi/niassa).

* Building
```
mvn clean install
```

* Testing
```
mvn clean verify \
-Djava_opts="-Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication" \
-DrunTestThreads=2 \
-DskipITs=false \
-DskipRunITs=false \
-DworkingDirectory=/path/to/tmp/ \
-DschedulingHost=niassa_oozie_host \
-DwebserviceUrl=http://niassa-url:8080 \
-DwebserviceUser=niassa_user \
-DwebservicePassword=niassa_user_password \
-Dcromwell-host=http://cromwell-url:8000
```

## Support

For support, please file an issue on the [Github project](https://github.com/oicr-gsi) or send an email to gsi@oicr.on.ca .

_Generated with wdl_doc_gen (https://github.com/oicr-gsi/wdl_doc_gen/)_
