package com.juebag.rrdbulkwriter;

import static org.rrd4j.ConsolFun.AVERAGE;
//import static org.rrd4j.ConsolFun.MAX;
import static org.rrd4j.ConsolFun.LAST;
import static org.rrd4j.DsType.GAUGE;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.rrd4j.ConsolFun;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.Sample;
import org.rrd4j.core.Util;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphDef;
import org.rrd4j.graph.TimeLabelFormat;


/**
 * Reads data saved by a Persistence Service of openHAB and stores it into an .rrd file to be read by rrdj-Persistence.
 * Data is read by a single REST API call from the selected Persistence Service.
 */
public class RRDBulkWriter {

    static final int IMG_WIDTH = 500;  
    static final int IMG_HEIGHT = 300;
    static final int MAX_STEP = 60; 
    private static final String STATE = "state";
    
    private RRDBulkWriter() {}

    /**
     * <p>
     * To start the BulkWriter, use the following command:
     * </p>
     * <pre>
     * java -cp rrdbulkwriter-1.0-jar-with-dependencies com.juebag.rrdbulkwriter.RRDBulkWriter
     * </pre>
     * 
     * 
     * @param args the name of the backend factory to use (optional)
     * @throws java.io.IOException  Thrown
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        System.setProperty("java.awt.headless", "true");
 
        println("== Starting RRDBulkWriter");
        Properties prop = new Properties();
        String fileName = "RRDBulkWriter.config";
        InputStream is = null;
        try {
            is = new FileInputStream(fileName);
        } catch (FileNotFoundException ex) {
            println("No RRDBulkWriter.config file found! Can't work without, bailing out!");
            return;
        }
        try {
            prop.load(is);
        } catch (IOException ex) {
            println("Data from RRDBulkWriter.config could not be read! Can't work without, bailing out!");
            return;
        }
        String time= prop.getProperty("start");
        String[] timeArray=time.split(",");
        long start = Util.getTimestamp(Integer.parseInt(timeArray[0]),Integer.parseInt(timeArray[1]),Integer.parseInt(timeArray[2]),Integer.parseInt(timeArray[3]),Integer.parseInt(timeArray[4]));
        time= prop.getProperty("end");
        timeArray=time.split(",");
        long end= Util.getTimestamp(Integer.parseInt(timeArray[0]),Integer.parseInt(timeArray[1]),Integer.parseInt(timeArray[2]),Integer.parseInt(timeArray[3]),Integer.parseInt(timeArray[4]));
        String file=prop.getProperty("file");
        String openHABServer=prop.getProperty("openhabserver");
        String persistenceService=prop.getProperty("persistenceservice");
        Integer archiveSetUp=Integer.parseInt(prop.getProperty("archivesetup"));

     
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH'%3A'mm'%3A'ss.000'Z'"); // correct format for REST API call!
        Date startDate = new Date(start*1000);
        Date endDate = new Date(end * 1000);
        
        //files could consist of several itemnames!
        String[] filenames;
        if (file.contains(",")) {
            filenames=file.split(",");
        } else {
            filenames = new String[] { file };
        }
        for (String f : filenames) {
            String logPath = Util.getRrd4jDemoPath(f + ".log");
            PrintWriter log = new PrintWriter(new BufferedOutputStream(new FileOutputStream(logPath, false)));

            String rrdPath = Util.getRrd4jDemoPath(f + ".rrd");
            String imgPath = Util.getRrd4jDemoPath(f + ".png");

            println("== Start: " + df.format(startDate));
            println("== End: " + df.format(endDate));
            log.println("== Start: " + df.format(startDate));
            log.println("== End: " + df.format(endDate));
            if (startDate.after(endDate)) {
                println("Requested Start is after End! Can't work that way, bailing out!");
                log.println("Requested Start is after End! Can't work that way, bailing out!");
                log.close();
                return;
            }
            log.println("== Creating RRD file " + rrdPath);
            RrdDef rrdDef = new RrdDef(rrdPath, start - 1, MAX_STEP);
            rrdDef.setVersion(2);
            ConsolFun cFunc = AVERAGE;
            switch (archiveSetUp) {
                case 1:
                    //Database setup according OH2 default
                    cFunc = AVERAGE;
                    rrdDef.addDatasource(STATE, GAUGE, 60, Double.NaN, Double.NaN);
                    rrdDef.addArchive(cFunc, 0.5, 1, 480);
                    rrdDef.addArchive(cFunc, 0.5, 4, 360);
                    rrdDef.addArchive(cFunc, 0.5, 14, 644);
                    rrdDef.addArchive(cFunc, 0.5, 60, 720);
                    rrdDef.addArchive(cFunc, 0.5, 720, 730);
                    rrdDef.addArchive(cFunc, 0.5, 10080, 520);
                    break;
                case 2:
                    //Database setup according OH3 default_numeric
                    cFunc = LAST;
                    rrdDef.addDatasource(STATE, GAUGE, 600, Double.NaN, Double.NaN);
                    rrdDef.addArchive(cFunc, 0.5, 1, 360);
                    rrdDef.addArchive(cFunc, 0.5, 6, 10080);
                    rrdDef.addArchive(cFunc, 0.5, 90, 36500);
                    rrdDef.addArchive(cFunc, 0.5, 360, 43800);
                    rrdDef.addArchive(cFunc, 0.5, 8640, 3650);
                    break;
                case 3:
                    //Database setup according OH3 default_quantifiable
                    cFunc = AVERAGE;
                    rrdDef.addDatasource(STATE, GAUGE, 600, Double.NaN, Double.NaN);
                    rrdDef.addArchive(cFunc, 0.5, 1, 360);
                    rrdDef.addArchive(cFunc, 0.5, 6, 10080);
                    rrdDef.addArchive(cFunc, 0.5, 90, 36500);
                    rrdDef.addArchive(cFunc, 0.5, 360, 43800);
                    rrdDef.addArchive(cFunc, 0.5, 8640, 3650);
                    break;
                default:
                    //Database setup according OH2 default (could be changed for custom setup!)
                    cFunc = AVERAGE;
                    rrdDef.addDatasource(STATE, GAUGE, 60, Double.NaN, Double.NaN);
                    rrdDef.addArchive(cFunc, 0.5, 1, 480);
                    rrdDef.addArchive(cFunc, 0.5, 4, 360);
                    rrdDef.addArchive(cFunc, 0.5, 14, 644);
                    rrdDef.addArchive(cFunc, 0.5, 60, 720);
                    rrdDef.addArchive(cFunc, 0.5, 720, 730);
                    rrdDef.addArchive(cFunc, 0.5, 10080, 520);
            }
            log.println(rrdDef.dump());
            log.println("Estimated file size: " + rrdDef.getEstimatedSize());
            try (RrdDb rrdDb = RrdDb.of(rrdDef)) {
                log.println("== RRD file created.");
                if (rrdDb.getRrdDef().equals(rrdDef)) {
                    log.println("Checking RRD file structure... OK");
                } else {
                    println("Invalid RRD file created. This is a serious bug, bailing out!");
                    log.println("Invalid RRD file created. This is a serious bug, bailing out!");
                    log.close();
                    return;
                }
            }
            println("== (empty) RRD file closed.");
            log.println("== (empty) RRD file closed.");

            //Getting the data from REST API
            println("Getting source-data via REST API call!");
            log.println("Getting source-data via REST API call!");

            String resturl = "http://" + openHABServer + ":8080/rest/persistence/items/" + f + "?serviceId="
                    + persistenceService + "&starttime=" + df.format(startDate) + "&endtime=" + df.format(endDate);
            log.println("== REST API Call: " + resturl);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(resturl)).build();
            String json = "";
            try {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                json = response.body();
            } catch (Exception e) {
                println("HTTP Request failed, is openHABServer correctly set? Bailing out!");
                log.println("HTTP Request failed, is openHABServer correctly set? Bailing out!");
                log.close();
                return;
            }
            JSONObject obj = new JSONObject(json);
            JSONArray arr = obj.getJSONArray("data");
            if (arr.length() == 0) {
                println("No data found in JSON-String! Bailing out!");
                log.println("No data found in JSON-String! Bailing out!");
                log.close();
                return;
            }
            NavigableMap<Long, Double> valueMap = new TreeMap<Long, Double>();
            log.println("Reading the data");
            for (int i = 0; i < arr.length(); i++) {
                Long key = Long.valueOf(arr.getJSONObject(i).getLong("time")) / 1000; // divide by 1000 to get time in seconds!!
                Double value = Double.parseDouble(arr.getJSONObject(i).getString("state"));
                valueMap.put(key, value);
                log.println("key: " + key + " value: " + value);
                println("key: " + key + " value: " + value);
            }
            // read the source data and write into .rrd file
            println("== writing values into .rrd every " + MAX_STEP + " seconds");
            log.println("== writing values into .rrd every " + MAX_STEP + " seconds");
            Map.Entry<Long, Double> currentDataPoint = valueMap.firstEntry();

            long t = start;
            try (RrdDb rrdDb = RrdDb.of(rrdPath)) {
                Sample sample = rrdDb.createSample();
                while (t <= end + MAX_STEP) {
                    sample.setTime(t);
                    if (currentDataPoint.getKey() < t) {
                        currentDataPoint = valueMap.higherEntry(currentDataPoint.getKey()); // use next datapoint only if the actual timekey is lower then t! 
                    }
                    if (currentDataPoint == null) {
                        log.println("No more saved data!");
                        break;
                    }
                    sample.setValue(STATE, currentDataPoint.getValue());
                    log.println(sample.dump());
                    sample.update();
                    t += MAX_STEP;
                }
            }

            // create graph
            log.println("== Creating graph from the file");
            RrdGraphDef gDef = new RrdGraphDef(start, end);
            gDef.setTimeLabelFormat(new CustomTimeLabelFormat());
            gDef.setLocale(Locale.US);
            gDef.setWidth(IMG_WIDTH);
            gDef.setHeight(IMG_HEIGHT);

            // To use rrdtool font set or not
            //gDef.setFontSet(true);

            gDef.setFilename(imgPath);
            gDef.setStartTime(start);
            gDef.setEndTime(end);
            df = new SimpleDateFormat("yyyy-MM-dd'T'HH':'mm'Z'"); // correct format for REST API call!
            gDef.setTitle("Values from " + df.format(startDate) + " to " + df.format(endDate));
            //gDef.setTitle("Values from " + start + " to " + end);
            gDef.setVerticalLabel("Values");
            gDef.datasource(STATE, rrdPath, STATE, cFunc);
            gDef.line(STATE, Color.BLUE, "imported values");
            gDef.comment("\\r");
            gDef.setImageInfo("<img src='%s' width='%d' height = '%d'>");
            gDef.setPoolUsed(false);
            gDef.setImageFormat("png");
            gDef.setDownsampler(new eu.bengreen.data.utility.LargestTriangleThreeBuckets((int) (IMG_WIDTH * 1)));
            // create graph finally
            RrdGraph graph = new RrdGraph(gDef);
            println(graph.getRrdGraphInfo().dump());
            println("== Graph created " + Util.getLapTime());
            log.println(graph.getRrdGraphInfo().dump());
            log.close();
        }
    }

    static void println(String msg) {
        System.out.println(msg);
    }

    static void print(String msg) {
        System.out.print(msg);
    }

    static class CustomTimeLabelFormat implements TimeLabelFormat {
        public String format(Calendar c, Locale locale) {
            if (c.get(Calendar.MILLISECOND) != 0) {
                return String.format(locale, "%1$tH:%1$tM:%1$tS.%1$tL", c);
            } else if (c.get(Calendar.SECOND) != 0) {
                return String.format(locale, "%1$tH:%1$tM:%1$tS", c);
            } else if (c.get(Calendar.MINUTE) != 0) {
                return String.format(locale, "%1$tH:%1$tM", c);
            } else if (c.get(Calendar.HOUR_OF_DAY) != 0) {
                return String.format(locale, "%1$tH:%1$tM", c);
            } else if (c.get(Calendar.DAY_OF_MONTH) != 1) {
                return String.format(locale, "%1$td %1$tb", c);
            } else if (c.get(Calendar.DAY_OF_YEAR) != 1) {
                return String.format(locale, "%1$td %1$tb", c);
            } else {
                return String.format(locale, "%1$tY", c);
            }
        }
    }
}


