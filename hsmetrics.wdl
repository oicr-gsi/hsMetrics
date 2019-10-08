version 1.0

workflow hsMetrics {
input {
   File    inputBam
   File    baitBed
   File    targetBed
   String? outputFileNamePrefix = ""
   String? stringencyFilter = "LENIENT"
   Float?  minPct      = 0.5
   Int?    coverageCap = 500
}

String? outputPrefix = if outputFileNamePrefix=="" then basename(inputBam, '.bam') else outputFileNamePrefix

call makeRefDictionary {}
call bedToIntervals as bedToTargetIntervals { input: inputBed = targetBed, refDict = makeRefDictionary.refDict }
call bedToIntervals as bedToBaitIntervals { input: inputBed = baitBed, refDict = makeRefDictionary.refDict }

call collectHSmetrics{ input: inputBam = inputBam, baitIntervals = bedToBaitIntervals.outputIntervals, targetIntervals = bedToTargetIntervals.outputIntervals, filter = stringencyFilter, coverageCap = coverageCap, outputPrefix = outputPrefix }
call collectInsertMetrics{ input: inputBam = inputBam, minPct = minPct, outputPrefix = outputPrefix }

meta {
 author: "Peter Ruzanov"
 email: "peter.ruzanov@oicr.on.ca"
 description: "HSMetrics 2.0"
}

output {
  File outputHSMetrics  = collectHSmetrics.outputHSMetrics
  File outputINSMetrics = collectInsertMetrics.outputINSMetrics
  File outputINSPDF     = collectInsertMetrics.outputINSPDF
}

}

# ==========================================
#  TASK 1 of 4: make reference dictionary
# ==========================================
task makeRefDictionary {
input {
   String refFasta
   Int?   javaMemory = 10
   String? modules   = "java/8 picard/2.19.2 hg19/p13"
}

parameter_meta {
 refFasta: "Path to fasta reference file"
 javaMemory: "Memory allocated to java"
 modules: "Names and versions of modules needed"
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar CreateSequenceDictionary \
                              REFERENCE=~{refFasta} \
                              OUTPUT="~{basename(refFasta, '.fa')}.dict" 
>>>

runtime {
  memory:  "~{javaMemory + 6} GB"
  modules: "~{modules}"
}

output {
  File refDict = "~{basename(refFasta, '.fa')}.dict"
}
}

# ==========================================
#  TASK 2 of 4: convert bed to intervals
# ==========================================
task bedToIntervals {
input {
   File   inputBed
   File   refDict       
   Int?   javaMemory = 12
   String? modules   = "java/8 picard/2.19.2" 
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar BedToIntervalList \
                              INPUT=~{inputBed} \
                              OUTPUT="~{basename(inputBed, '.bed')}.interval_list" \
                              SD="~{refDict}"
>>>

parameter_meta {
 inputBed: "Input bed file"
 refDict: "Path to index of fasta reference file"
 javaMemory: "Memory allocated to java"
 modules: "Names and versions of modules needed"
}

runtime {
  memory:  "~{javaMemory + 6} GB"
  modules: "~{modules}"
}

output {
  File outputIntervals = "~{basename(inputBed, '.bed')}.interval_list"
}
}


# ==========================================
#  TASK 3 of 4: collect HS metric
# ==========================================
task collectHSmetrics {
input { 
   File   inputBam
   File   baitIntervals
   File   targetIntervals
   String? refFasta   = "$HG19_ROOT/hg19_random.fa"
   String? metricTag  = "HS"
   String? filter     = "LENIENT"
   String? outputPrefix = "OUTPUT"
   Int?   javaMemory  = 12
   Int?   coverageCap = 500
   String? modules    = "java/8 picard/2.19.2 hg19/p13"
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar CollectHsMetrics \
                              TMP_DIR=picardTmp \
                              BAIT_INTERVALS=~{baitIntervals} \
                              TARGET_INTERVALS=~{targetIntervals} \
                              R=~{refFasta} \
                              COVERAGE_CAP=~{coverageCap} \
                              INPUT=~{inputBam} \
                              OUTPUT="~{outputPrefix}.~{metricTag}" \
                              VALIDATION_STRINGENCY=~{filter} 
>>>

parameter_meta {
 inputBam: "Input bam file"
 baitIntervals: "bed file with bait intervals"
 targetIntervals: "bed file with target intervals"
 refFasta: "Path to fasta reference file"
 metricTag: "Extension for metrics file"
 filter: "Settings for picard filter"
 outputPrefix: "prefix to build a name for output file"
 coverageCap: "Parameter to set a max coverage limit for Theoretical Sensitivity calculations"
 javaMemory: "Memory allocated to java"
 modules: "Names and versions of modules needed"
}

runtime {
  memory:  "~{javaMemory + 6} GB"
  modules: "~{modules}"
}

output {
  File outputHSMetrics = "~{outputPrefix}.~{metricTag}"
}
}


# ==========================================
#  TASK 4 of 4: collect Insert metrics
# ==========================================
task collectInsertMetrics {
input {
   File    inputBam
   String? metricTag  = "INS"
   Int?    javaMemory = 12
   Float?  minPct     = 0.5
   String? outputPrefix = "OUTPUT"
   String? modules    = "java/8 rstats/3.6 picard/2.19.2"
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar CollectInsertSizeMetrics \
                              INPUT=~{inputBam} \
                              OUTPUT="~{outputPrefix}.~{metricTag}" \
                              H="~{outputPrefix}.~{metricTag}.PDF" \
                              M=~{minPct}
>>>

parameter_meta {
 inputBam: "Input bam file"
 metricTag: "Extension for metrics file"
 minPct: "Discard any data categories (out of FR, TANDEM, RF) that have fewer than this percentage of overall reads"
 outputPrefix: "prefix to build a name for output file"
 javaMemory: "Memory allocated to java"
 modules: "Names and versions of modules needed"
}

runtime {
  memory:  "~{javaMemory + 6} GB"
  modules: "~{modules}"
}

output {
  File outputINSMetrics = "~{outputPrefix}.~{metricTag}"
  File outputINSPDF     = "~{outputPrefix}.~{metricTag}.PDF"
}
}

