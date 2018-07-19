package ca.on.oicr.pde.deciders;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.sourceforge.seqware.common.hibernate.FindAllTheFiles.Header;
import net.sourceforge.seqware.common.module.FileMetadata;
import net.sourceforge.seqware.common.module.ReturnValue;
import net.sourceforge.seqware.common.util.Log;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author prath@oicr.on.ca
 */
public class HSMetricsDecider extends OicrDecider {

    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
    private Map<String, BeSmall> fileSwaToSmall;

    private String templateType = null;
    private String queue = "";
    private String baseName;
    private int coverageCap = 500;
    private float minPCT = (float) 0.5;
    private String stringency = "LENIENT";
    private int javaMem = 16;
    private String baitBed;
    private String targetBed;
    private String refFasta;
    private String refDict;

    private final static String BAM_METATYPE = "application/bam";
    private String tumorType;


    public HSMetricsDecider() {
        super();
        fileSwaToSmall = new HashMap<String, BeSmall>();
        parser.acceptsAll(Arrays.asList("ini-file"), "Optional: the location of the INI file.").withRequiredArg();
        parser.accepts("template-type", "Required. Set the template type to limit the workflow run "
                + "so that it runs on data only of this template type").withRequiredArg();
        parser.accepts("queue", "Optional: Set the queue (Default: not set)").withRequiredArg();
        parser.accepts("minimum-pct", "Optional: Specify minimum percentage for overal reads").withOptionalArg();
        parser.accepts("coverage-cap", "Optional: Specify maximum coverage limit").withOptionalArg();
        parser.accepts("validation-stringency", "Optional: Specify VALIDATION_STRINGENCY ; STRICT, LENIENT, SILENT").withOptionalArg();
        parser.accepts("java-mem-gb", "Optional: Specify java memory in GBs").withOptionalArg();
        parser.accepts("bait-file", "Required. Specify bait file, in bed format").withRequiredArg();
        parser.accepts("target-file", "Required. Specify target file, in bed format").withRequiredArg();
        parser.accepts("ref-fasta", "Required. Specify reference fasta").withRequiredArg();
        parser.accepts("ref-dict", "Required. Specify reference fasta dict").withRequiredArg();
    }

    @Override
    public ReturnValue init() {
        Log.debug("INIT");
        this.setMetaType(Arrays.asList(BAM_METATYPE));
        this.setHeadersToGroupBy(Arrays.asList(Header.FILE_SWA));

        ReturnValue rv = super.init();
        rv.setExitStatus(ReturnValue.SUCCESS);

        //Group by sample if no other grouping selected
        if (this.options.has("group-by")) {
            Log.error("group-by parameter passed, but this decider does not allow overriding the default grouping (by Donor + Library Type)");
        }

        if (this.options.has("queue")) {
            this.queue = options.valueOf("queue").toString();
        }

        if (this.options.has("template-type")) {
            this.templateType = options.valueOf("template-type").toString();
        } else {
            if (!options.hasArgument("template-type")) {

                Log.error("--template-type requires an argument, e.g. EX,WT");
                rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
                return rv;
            }
        }
        
        if (this.options.has("coverage-cap")) {
            this.coverageCap = Integer.parseInt(options.valueOf("coverage-cap").toString());
        }
        
        if (this.options.has("minimim-pct")) {
            this.minPCT = Float.parseFloat(options.valueOf("minimum-pct").toString());
        }
        
        if (this.options.has("validation-stringency")) {
            this.stringency = options.valueOf("validation-stringency").toString();
        }
        
        if (this.options.has("java-mem-gb")){
            this.javaMem = Integer.parseInt(options.valueOf("java-mem-gb").toString());
        }
        
        if (!this.options.has("bait-file")){
            Log.error("--bait-file requires a bait bed file");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
        } else {
            this.baitBed = this.options.valueOf("bait-file").toString();
            if (this.baitBed == null){
                Log.error("--bait-file contains NULL");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
            }
        }
        
        if (!this.options.has("target-file")){
            Log.error("--target-file requires a target bed file");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
        } else {
            this.targetBed = this.options.valueOf("target-file").toString();
            if (this.targetBed == null){
                Log.error("--target-file contains NULL");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
            }
        }
        
        if (!this.options.has("ref-fasta")){
            Log.error("--ref-fasta requires a target bed file");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
        } else {
            this.refFasta = this.options.valueOf("ref-fasta").toString();
            if (this.refFasta == null){
                Log.error("--ref-fasta contains NULL");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
            }
        }
        
        if (!this.options.has("ref-dict")){
            Log.error("--ref-dict requires a target bed file");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
        } else {
            this.refDict = this.options.valueOf("ref-dict").toString();
            if (this.refDict == null){
                Log.error("--ref-dict contains NULL");
            rv.setExitStatus(ReturnValue.INVALIDARGUMENT);
            return rv;
            }
        }
        

        return rv;
    }

    /**
     * Final check
     *
     * @param commaSeparatedFilePaths
     * @param commaSeparatedParentAccessions
     *
     * @return
     */
    @Override
    protected ReturnValue doFinalCheck(String commaSeparatedFilePaths, String commaSeparatedParentAccessions) {
        String[] filePaths = commaSeparatedFilePaths.split(",");
        boolean haveBam = false;

        // Check for duplicate file names and exclude them from analysis

        for (String p : filePaths) {

            for (BeSmall bs : fileSwaToSmall.values()) {
                if (!bs.getPath().equals(p)) {
                    continue;
                }
                String tt = bs.getTissueType();

                if (!tt.isEmpty()) {
                    haveBam = true;
                }
            }
        }
        if (haveBam && (filePaths.length == 1)) { // check one file per filePaths
            return super.doFinalCheck(commaSeparatedFilePaths, commaSeparatedParentAccessions);
        } 
        Log.error("Data not available, WON'T RUN");
        return new ReturnValue(ReturnValue.INVALIDPARAMETERS);
    }

    @Override
    protected boolean checkFileDetails(ReturnValue returnValue, FileMetadata fm) {
        Log.debug("CHECK FILE DETAILS:" + fm);
        String currentTtype = returnValue.getAttribute(Header.SAMPLE_TAG_PREFIX.getTitle() + "geo_library_source_template_type");
        String currentTissueType = returnValue.getAttribute(Header.SAMPLE_TAG_PREFIX.getTitle() + "geo_tissue_type");

        if (null == currentTissueType) {
            return false; // we need only those which have their tissue type set
        }

        // Filter the data of a different template type if filter is specified
        if (!this.templateType.equalsIgnoreCase(currentTtype)) {
            Log.warn("Excluding file with SWID = [" + returnValue.getAttribute(Header.FILE_SWA.getTitle())
                    + "] due to template type/geo_library_source_template_type = [" + currentTtype + "]");
            return false;
        }

        // Do not process tumor tissues of type that doesn't match set parameter
        if (null != this.tumorType) {
            return false;
        }

        return super.checkFileDetails(returnValue, fm);
    }

    @Override
    public Map<String, List<ReturnValue>> separateFiles(List<ReturnValue> vals, String groupBy) {
        Log.debug("Number of files from file provenance = " + vals.size());

        // get files from study
        Map<String, ReturnValue> iusDeetsToRV = new HashMap<String, ReturnValue>();
        // Override the supplied group-by value
        for (ReturnValue currentRV : vals) {
            boolean metatypeOK = false;

            for (int f = 0; f < currentRV.getFiles().size(); f++) {
                try {
                    if (currentRV.getFiles().get(f).getMetaType().equals(BAM_METATYPE)) {
                        metatypeOK = true;
                    }
                } catch (Exception e) {
                    Log.stderr("Error checking a file");
                }
            }

            if (!metatypeOK) {
                continue; // Go to the next value
            }

            BeSmall currentSmall = new BeSmall(currentRV);
            fileSwaToSmall.put(currentRV.getAttribute(groupBy), currentSmall);

            String fileDeets = currentSmall.getIusDetails();
            Date currentDate = currentSmall.getDate();

            //if there is no entry yet, add it
            if (iusDeetsToRV.get(fileDeets) == null) {
                iusDeetsToRV.put(fileDeets, currentRV);
            } //if there is an entry, compare the current value to the 'old' one in
            //the map. if the current date is newer than the 'old' date, replace
            //it in the map
            else {
                ReturnValue oldRV = iusDeetsToRV.get(fileDeets);
                BeSmall oldSmall = fileSwaToSmall.get(oldRV.getAttribute(Header.FILE_SWA.getTitle()));
                Date oldDate = oldSmall.getDate();
                if (currentDate.after(oldDate)) {
                    iusDeetsToRV.put(fileDeets, currentRV);
                }
            }
        }

        //only use those files that entered into the iusDeetsToRV
        //since it's a map, only the most recent values
        List<ReturnValue> newValues = new ArrayList<ReturnValue>(iusDeetsToRV.values());
        Map<String, List<ReturnValue>> map = new HashMap<String, List<ReturnValue>>();

        //group files according to the designated header (e.g. sample SWID)
        for (ReturnValue r : newValues) {
            String currVal = fileSwaToSmall.get(r.getAttribute(Header.FILE_SWA.getTitle())).getGroupByAttribute();
            List<ReturnValue> vs = map.get(currVal);
            if (vs == null) {
                vs = new ArrayList<ReturnValue>();
            }
            vs.add(r);
            map.put(currVal, vs);
        }

        return map;
    }

    @Override
    protected String handleGroupByAttribute(String attribute) {
        String a = super.handleGroupByAttribute(attribute);
        BeSmall small = fileSwaToSmall.get(a);
        if (small != null) {
            return small.getGroupByAttribute();
        }
        return attribute;
    }

    @Override
    protected Map<String, String> modifyIniFile(String commaSeparatedFilePaths, String commaSeparatedParentAccessions) {

        String[] filePaths = commaSeparatedFilePaths.split(",");
        String inputBam = new String();
        for (String p : filePaths) {
            for (BeSmall bs : fileSwaToSmall.values()) {
                if (!bs.getPath().equals(p)) {
                    continue;
                }
                String tt = bs.getTissueType();
                if (!tt.isEmpty()) {
                    Log.stdout("WRITING TO INI FILE ... " + bs.getPath());
                    this.baseName = FilenameUtils.getBaseName(bs.getPath());
                    inputBam = bs.getPath();
                }  
                else{
                    Log.error("THE DONOR does not have data to run the workflow");
                    abortSchedulingOfCurrentWorkflowRun();
                }
            }
        }
        Map<String, String> iniFileMap = super.modifyIniFile(commaSeparatedFilePaths, commaSeparatedParentAccessions);
        iniFileMap.put("input_bam_file", inputBam);
        iniFileMap.put("data_dir", "data");
        iniFileMap.put("template_type", this.templateType);
        iniFileMap.put("output_filename_prefix", this.baseName);

        if (!this.queue.isEmpty()) {
            iniFileMap.put("queue", this.queue);
        }
        iniFileMap.put("coverage_cap", Integer.toString(this.coverageCap));
        iniFileMap.put("stringency_filter", this.stringency);
        iniFileMap.put("minimum_pct", Float.toString(this.minPCT));
        iniFileMap.put("java_mem", "-Xmx" + Integer.toString(this.javaMem) + "g");
        iniFileMap.put("target_bed", this.targetBed);
        iniFileMap.put("bait_bed", this.baitBed);
        return iniFileMap;
                

//        return super.modifyIniFile(commaSeparatedFilePaths, commaSeparatedParentAccessions);
    }

    public static void main(String args[]) {

        List<String> params = new ArrayList<String>();
        params.add("--plugin");
        params.add(HSMetricsDecider.class.getCanonicalName());
        params.add("--");
        params.addAll(Arrays.asList(args));
        System.out.println("Parameters: " + Arrays.deepToString(params.toArray()));
        net.sourceforge.seqware.pipeline.runner.PluginRunner.main(params.toArray(new String[params.size()]));

    }


    private class BeSmall {

        private Date date = null;
        private String iusDetails = null;
        private String groupByAttribute = null;
        private String tissueType = null;
        private String path = null;
        private String extName = null;
        private String groupID = null;
        private String groupDescription = null;
        private String workflowName = null;

        public BeSmall(ReturnValue rv) {
            try {
                date = format.parse(rv.getAttribute(Header.PROCESSING_DATE.getTitle()));
            } catch (ParseException ex) {
                Log.error("Bad date!", ex);
                ex.printStackTrace();
            }
            FileAttributes fa = new FileAttributes(rv, rv.getFiles().get(0));
            workflowName = rv.getAttribute(Header.WORKFLOW_NAME.getTitle());
            iusDetails = fa.getLibrarySample() + fa.getSequencerRun() + fa.getLane() + fa.getBarcode() + workflowName;
            tissueType = fa.getLimsValue(Lims.TISSUE_TYPE);
            extName = rv.getAttribute(Header.SAMPLE_TAG_PREFIX.getTitle() + "geo_external_name");
            if (null == extName || extName.isEmpty()) {
                extName = "NA";
            }
            groupID = fa.getLimsValue(Lims.GROUP_ID);
            if (null == groupID || groupID.isEmpty()) {
                groupID = "NA";
            }
            groupDescription = fa.getLimsValue(Lims.GROUP_DESC);
            if (null == groupDescription || groupDescription.isEmpty()) {
                groupDescription = "NA";
            }
            groupByAttribute = iusDetails;
            path = rv.getFiles().get(0).getFilePath() + "";
        }

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        public String getGroupByAttribute() {
            return groupByAttribute;
        }

        public void setGroupByAttribute(String groupByAttribute) {
            this.groupByAttribute = groupByAttribute;
        }

        public String getTissueType() {
            return tissueType;
        }

        public String getIusDetails() {
            return iusDetails;
        }

        public void setIusDetails(String iusDetails) {
            this.iusDetails = iusDetails;
        }

        public String getPath() {
            return path;
        }

        public String getExtName() {
            return extName;
        }

        public String getGroupID() {
            return groupID;
        }

        public String getGroupDescription() {
            return groupDescription;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    /**
     * Utility function
     *
     * @param path
     * @param extension
     *
     * @return
     */
    public static String makeBasename(String path, String extension) {
        return path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf(extension));
    }
}
