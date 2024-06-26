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
`inputBam`|File|Input bam file
`baitBed`|String|Path to input bait bed file
`targetBed`|String|Path to input target bed
`reference`|String|the reference genome for input sample


#### Optional workflow parameters:
Parameter|Value|Default|Description
---|---|---|---
`outputFileNamePrefix`|String|basename(inputBam,'.bam')|Prefix for output


#### Optional task parameters:
Parameter|Value|Default|Description
---|---|---|---
`bedToTargetIntervals.jobMemory`|Int|16|Memory allocated to job
`bedToTargetIntervals.timeout`|Int|1|Maximum amount of time (in hours) the task can run for.
`bedToBaitIntervals.jobMemory`|Int|16|Memory allocated to job
`bedToBaitIntervals.timeout`|Int|1|Maximum amount of time (in hours) the task can run for.
`collectHSmetrics.metricTag`|String|"HS"|Extension for metrics file
`collectHSmetrics.filter`|String|"LENIENT"|Settings for picard filter
`collectHSmetrics.jobMemory`|Int|18|Memory allocated to job
`collectHSmetrics.coverageCap`|Int|500|Parameter to set a max coverage limit for Theoretical Sensitivity calculations
`collectHSmetrics.maxRecordsInRam`|Int|250000|Specifies the N of records stored in RAM before spilling to disk. Increasing this number increases the amount of RAM needed.
`collectHSmetrics.timeout`|Int|5|Maximum amount of time (in hours) the task can run for.


### Outputs

Output | Type | Description | Labels
---|---|---|---
`outputHSMetrics`|File|File with HS metrics|vidarr_label: outputHSMetrics


## Commands
This section lists command(s) run by hsMetrics workflow
 
* Running hsMetrics
 
This workflow wraps one of the Picard's metrics tools
 
### Convert a bed file into interval list:
 
```
  java -Xmx[JOB_MEMORY-6]G -jar picard.jar BedToIntervalList
                                INPUT = INPUT_BED 
                                OUTPUT = INPUT_BED_BASENAME.interval_list
                                SD = REF_DICT
```
 
### Run HSmetrics:
 
```
  java -Xmx[JOB_MEMORY-6]G -jar picard.jar CollectHsMetrics 
                                TMP_DIR = picardTmp 
                                BAIT_INTERVALS = BAIT_INTERVALS
                                TARGET_INTERVALS = TARGET_INTERVALS
                                R = REF_FASTA
                                COVERAGE_CAP = COVERAGE_CAP
                                MAX_RECORDS_IN_RAM = MAX_RECORDS_IN_RAM
                                INPUT = INPUT_BAM
                                OUTPUT = OUTPUT_PREFIX.METRIC_TAG.txt
                                VALIDATION_STRINGENCY = FILTER
```
## Support

For support, please file an issue on the [Github project](https://github.com/oicr-gsi) or send an email to gsi@oicr.on.ca .

_Generated with generate-markdown-readme (https://github.com/oicr-gsi/gsi-wdl-tools/)_
