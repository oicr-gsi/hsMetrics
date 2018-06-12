package ca.on.oicr.pde.workflows;

import ca.on.oicr.pde.utilities.workflows.OicrWorkflow;
import java.util.Map;
import java.util.logging.Logger;
import net.sourceforge.seqware.pipeline.workflowV2.model.Command;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;
import net.sourceforge.seqware.pipeline.workflowV2.model.SqwFile;
import org.apache.commons.io.FilenameUtils;

/**
 * <p>
 * For more information on developing workflows, see the documentation at
 * <a href="http://seqware.github.io/docs/6-pipeline/java-workflows/">SeqWare
 * Java Workflows</a>.</p>
 *
 * Quick reference for the order of methods called: 1. setupDirectory 2.
 * setupFiles 3. setupWorkflow 4. setupEnvironment 5. buildWorkflow
 *
 * See the SeqWare API for
 * <a href="http://seqware.github.io/javadoc/stable/apidocs/net/sourceforge/seqware/pipeline/workflowV2/AbstractWorkflowDataModel.html#setupDirectory%28%29">AbstractWorkflowDataModel</a>
 * for more information.
 */
public class HSMetricsWorkflow extends OicrWorkflow {

    //dir
    private String dataDir, tmpDir;
    private String outDir;

    // Input Data
    private String inputBam;
    private String outputFilenamePrefix;
     

    //Tools
//    private String samtools;
    private String java;
    private String picard;
//    private String bedtools;
    //picard
    
    private String minpct;
    private String coverageCap;
    private String lenient;
    


    //Memory allocation
    private Integer picardMem;
    private String javaMem = "-Xmx16g";



    //ref Data
    private String refFasta;
    private String refDict;
//    private String captureBed;
    private String baitIntervals;
    private String targetIntervals;
    private String baitBed;
    private String targetBed;

    private boolean manualOutput;
    private String queue;

    
    // metatypes
    private String TXT_METATYPE="txt/plain";
    private String PDF_METATYPE="application/pdf";

    private void init() {
        try {
            //dir
            dataDir = "data/";
            tmpDir = getProperty("tmp_dir");

            // input samples 
            inputBam = getProperty("input_bam_file");
            
            //
            outputFilenamePrefix = getProperty("external_identifier");

            //tools
            java = getProperty("java");
            picard = getProperty("picard_jar").toString();

            // ref fasta
            refFasta = getProperty("ref_fasta");
            baitIntervals = getOptionalProperty("bait_intervals", "pass");
            targetIntervals = getOptionalProperty("target_intervals", "pass");
            baitBed = getProperty("bait_bed");
            targetBed = getOptionalProperty("target_bed", this.baitBed);
            refDict = getProperty("ref_dict");
            
            //picard
            coverageCap = getOptionalProperty("coverage_cap","500");
            lenient = getOptionalProperty("stringency_filter", "LENIENT");
            minpct = getOptionalProperty("minimum_pct", "0.5");

            manualOutput = Boolean.parseBoolean(getProperty("manual_output"));
            queue = getOptionalProperty("queue", "");

            picardMem = Integer.parseInt(getProperty("picard_mem"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setupDirectory() {
        init();
        this.addDirectory(dataDir);
        this.addDirectory(tmpDir);
        if (!dataDir.endsWith("/")) {
            dataDir += "/";
        }
        if (!tmpDir.endsWith("/")) {
            tmpDir += "/";
        }
    }

    @Override
    public Map<String, SqwFile> setupFiles() {
        SqwFile file0 = this.createFile("inBam");
        file0.setSourcePath(inputBam);
        file0.setType("application/bam");
        file0.setIsInput(true);
//        SqwFile file1 = this.createFile("bait_file");
//        file1.setSourcePath(baitIntervals);
//        file1.setType(TXT_METATYPE);
//        file1.setIsInput(true);       
        return this.getFiles();
    }

    @Override
    public void buildWorkflow() {
        Job parentJob = null;
        this.outDir = this.outputFilenamePrefix + "_output/";
        String inBam = getFiles().get("inBam").getProvisionedPath();
        
        String outMetrics1 = this.dataDir + this.outputFilenamePrefix + ".HS";
        String outMetrics2 = this.dataDir + this.outputFilenamePrefix + ".INS";
        String outPdf = this.dataDir + this.outputFilenamePrefix + ".INS.pdf";
        
        Job collectINS = picardCollectInsertMetrics(inBam, outMetrics2, outPdf);
        parentJob = collectINS;
        
        // Check for presence of target intervals file
        if ( this.targetIntervals == "pass" || this.targetIntervals == null ){
            Job bedToTarg = picardBedToInterval(this.targetBed);
            this.targetIntervals = this.tmpDir+ FilenameUtils.getBaseName(this.targetBed)+".interval_list";
            parentJob = bedToTarg;
        }
        if ( this.baitIntervals == "pass" || this.baitIntervals == null ){
            Job bedToTarg = picardBedToInterval(this.baitBed);
            this.baitIntervals = this.tmpDir + FilenameUtils.getBaseName(this.baitBed)+".interval_list";
            parentJob = bedToTarg;
        }
        Job collectHS = picardCollectHSMetrics(inBam, outMetrics1);
        collectHS.addParent(parentJob);
        
        // Provision out HS, HS2 and pdf
        SqwFile outHS2 = createOutputFile(outMetrics2, TXT_METATYPE, this.manualOutput);
        outHS2.getAnnotations().put("Insert_size_metrics", "picard");
        collectINS.addFile(outHS2);
        
        SqwFile outPDF = createOutputFile(outPdf, PDF_METATYPE, this.manualOutput);
        outPDF.getAnnotations().put("Insert_size_pdf", "picard");
        collectINS.addFile(outPDF);
        
        SqwFile outHS1 = createOutputFile(outMetrics1, TXT_METATYPE, this.manualOutput);
        outHS1.getAnnotations().put("HS_metrics", "picard");
        collectHS.addFile(outHS1);
    }
    
    private Job picardBedToInterval(String bedFile) {
        Job bedToInterval = getWorkflow().createBashJob("bed_to_interval");
        Command cmd = bedToInterval.getCommand();
        cmd.addArgument(this.java);
        cmd.addArgument(this.javaMem);
        cmd.addArgument("-jar " + this.picard + " BedToIntervalList");
        cmd.addArgument("I="+ bedFile);
        cmd.addArgument("O=" + this.tmpDir + FilenameUtils.getBaseName(bedFile)+".interval_list");
        cmd.addArgument("SD="+this.refDict);
        bedToInterval.setMaxMemory(Integer.toString(this.picardMem * 1024));
        bedToInterval.setQueue(getOptionalProperty("queue", ""));
        return bedToInterval;
    }   

    
    private Job picardCollectHSMetrics(String inBam, String outMetrics) {
        Job collectHSMetrics = getWorkflow().createBashJob("collect_hs_metrics");
        Command cmd = collectHSMetrics.getCommand();
        cmd.addArgument(this.java);
        cmd.addArgument(this.javaMem);
        cmd.addArgument("-jar " + this.picard + " CollectHsMetrics");
        cmd.addArgument("BAIT_INTERVALS="+ this.baitIntervals);
        cmd.addArgument("TARGET_INTERVALS=" + this.targetIntervals);
        cmd.addArgument("R="+this.refFasta);
        cmd.addArgument("INPUT=" + inBam);
        cmd.addArgument("OUTPUT=" + outMetrics);
        cmd.addArgument("COVERAGE_CAP=" + this.coverageCap);
        cmd.addArgument("VALIDATION_STRINGENCY="+ this.lenient);
        collectHSMetrics.setMaxMemory(Integer.toString(this.picardMem * 1024));
        collectHSMetrics.setQueue(getOptionalProperty("queue", ""));
        return collectHSMetrics;
    }   
    
        private Job picardCollectInsertMetrics(String inBam, String outMetrics, String outPdf) {
        Job collectInsertMetrics = getWorkflow().createBashJob("collect_insert_metrics");
        Command cmd = collectInsertMetrics.getCommand();
        cmd.addArgument(this.java);
        cmd.addArgument(this.javaMem);
        cmd.addArgument("-jar " + this.picard+ " CollectInsertSizeMetrics");
        cmd.addArgument("I="+ inBam);
        cmd.addArgument("O=" + outMetrics);
        cmd.addArgument("H=" + outPdf);
        cmd.addArgument("M=" + this.minpct);
        collectInsertMetrics.setMaxMemory(Integer.toString(this.picardMem * 1024));
        collectInsertMetrics.setQueue(getOptionalProperty("queue", ""));
        return collectInsertMetrics;
    } 

}