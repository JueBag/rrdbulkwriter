# RRDBulkWriter


Java Tool for openHAB users.

The tool has a single method "RRDBulkWriter" which will fetch the persisted data from a single item and put it into an rrd4j file (.rrd).
The method is located under "src/main/java/com/juebag/rrdbulkwriter".

The method can be called from a compiled .jar file with

"java -cp rrdbulkwriter-1.0-jar-with-dependencies com.juebag.rrdbulkwriter.RRDBulkWriter".

Some output is printed on the terminal to show the process is working, the complete log and all other output is stored under "/user/rrd4j-demo".
The output consists of the requested <itemname>.rrd file, the <itemname>.log and an <itemname>.png graphic file showing a graph of the data.
A compiled .jar is included in the "target" folder.
  
# Configuration
The configuration file "RRDBulkWriter.config" is expected in the folder of the .jar. An example is loacated next to this README.
| Setting                | Description                                                                                            |
| -----------------------| ------------------------------------------------------------------------------------------------------ |
| `-file`                | Name of the item to be persisted                                                                       |
| `-start`               | Start Time format yyyy, mm (Zero based!), dd, hh, mm  (Example 2020, 0, 1, 0, 0 = 2020 Jan 1st, 00:00Z)|
| `-stop`                | Start Time format yyyy, mm (Zero based!), dd, hh, mm  (Example 2020, 0, 1, 0, 0 = 2020 Jan 1st, 00:00Z)|
| `-openhabserver`       | Name or IP of openHAB Server                                                                           |
| `-persistenceservice`  | Name of persistence service that was used originally                                                   |
| `-archivesetup`        | Selected archive setup ( 1 = OH2 default, 2 = OH3 default_numeric, 3 = OH3 default_quantifiable)        |


For the selection of the archive type please refer to the [openHAB rrd4j documentation](https://openhab.org/addons/persistence/rrd4j/#default-datasource) and [openHAB version 2 rrd4j documentation](https://v2.openhab.org/v2.5/addons/persistence/rrd4j/#example), all default setup details are listed there.
Understanding the archive setup is neccessary in order to select a meaningfull setup for start- and endtime. 
Requesting a duration between start- and endtime lasting longer then the timeframe covered by the last archive (i.e. the archive with the longest duration) cause the tool only to run longer, it will not put in more data then the last archive can take.
During the run a datapoint will be created for each minute of the duration, rrd4j will then consolidate those datapoints according to the archive setup. 
If there is no datapoint for a specific minute in the source database the tool will copy the value from the last data in the the source database.


