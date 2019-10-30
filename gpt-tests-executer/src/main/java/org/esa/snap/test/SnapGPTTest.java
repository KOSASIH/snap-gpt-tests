package org.esa.snap.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;

public class SnapGPTTest {
    private static void printHelp() {
        System.out.println("Usage: SnapGPTTest properties scope jsonPath reportFolder [optional --Profiling=on|off]");
    }

    public static void main(String[] args) throws IOException {

        final String PROPERTYNAME_FAIL_ON_MISSING_DATA = "snap.gpt.tests.failOnMissingData";
        final boolean FAIL_ON_MISSING_DATA = Boolean.parseBoolean(System.getProperty(PROPERTYNAME_FAIL_ON_MISSING_DATA, "true"));


        boolean specificJSON = true;
        boolean success = true;
        //TODO check better the arguments
        if(args.length < 4) {
            printHelp();
            return;
        }
        // flag to enable/disable the profiling
        boolean profiler = true;
        // check if an extra parameter is passed set the profile variable 
        if (args.length == 5) { 
            // check that the flag is correct
            if (args[4].startsWith("--Profiling=")) {
                // extract the variable value and set the profiling flag
                String option = args[4].split("=")[1];
                profiler = option.equals("on");
            } else {
                // otherwise print help and exit
                printHelp();
                return;
            }      
        }

        Path testFolder = null;
        Path graphFolder= null;
        Path inputFolder = null;
        Path expectedOutputFolder = null;
        Path tempFolder = null;
        Path snapBinFolder = null;

        Properties properties = new Properties();

        try (FileReader in = new FileReader(args[0])){
            properties.load(in);
        } catch (IOException e) {
            System.out.println("Unable to load property file");
            return;
        }

        if (!FAIL_ON_MISSING_DATA) {
            System.out.println("Tests will not fail if test data is missing!");
        }

        testFolder = Paths.get(properties.getProperty("testFolder"));
        graphFolder= Paths.get(properties.getProperty("graphFolder"));
        inputFolder = Paths.get(properties.getProperty("inputFolder"));
        expectedOutputFolder = Paths.get(properties.getProperty("expectedOutputFolder"));
        tempFolder = Paths.get(properties.getProperty("tempFolder"));
        snapBinFolder = Paths.get(properties.getProperty("snapBin"));

        if(testFolder == null || graphFolder == null || inputFolder == null || expectedOutputFolder == null || tempFolder == null) {
            System.out.println("Some folder is null");
            return;
        }

        String scope = args[1];
        Path jsonPath = Paths.get(args[2]);
        if (Files.notExists(jsonPath)) {
            System.out.println("JSON file does not exist: " + jsonPath.toString());
            return;
        }
        String JSONFileName = jsonPath.getFileName().toString().substring(0,jsonPath.getFileName().toString().indexOf("."));

        Path reportFolderPath = Paths.get(args[3]);


        if (Files.notExists(reportFolderPath)) {
            System.out.println("Report folder does not exist: " + reportFolderPath.toString());
            return;
        }

        BufferedWriter writer = null;
        boolean report = true;
        try {
            writer = new BufferedWriter(new FileWriter(reportFolderPath.resolve(String.format("Report_%s.txt", JSONFileName)).toFile(), false));
        } catch (IOException e) {
            System.out.println("Cannot create report file: " + reportFolderPath.resolve(String.format("Report_%s.txt", JSONFileName)).toString());
            report = false;
        }


        Collection<File> fileList = null;
        if(specificJSON) {
            fileList = new ArrayList<>();
            fileList.add(jsonPath.toFile());
        } else {
            fileList = FileUtils.listFiles(testFolder.toFile(), new WildcardFileFilter("*.json"), TrueFileFilter.INSTANCE);
        }


        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        for(File file : fileList) {
            GraphTest[] graphTests = GraphTestsUtils.mapGraphTests(file);
            if(graphTests == null || graphTests.length == 0) {
                continue;
            }
            for(GraphTest graphTest : graphTests) {
                if(!graphTest.inputExists(inputFolder) && !FAIL_ON_MISSING_DATA) {
                    System.out.println(graphTest.getId() +" is missing input data in input folder '" + inputFolder + "'. Skipping test.");
                    continue;
                }

                boolean hasToBeExecuted = false;
                if(scope.toLowerCase().equals("release")) {
                    if (graphTest.getFrequency().toLowerCase().contains("release") ||
                            graphTest.getFrequency().toLowerCase().contains("weekly") ||
                            graphTest.getFrequency().toLowerCase().contains("daily")) {
                        hasToBeExecuted = true;
                    }
                } else if (scope.toLowerCase().equals("weekly")) {
                    if (graphTest.getFrequency().toLowerCase().contains("weekly") ||
                            graphTest.getFrequency().toLowerCase().contains("daily")) {
                        hasToBeExecuted = true;
                    }
                } else {
                    if (graphTest.getFrequency().toLowerCase().contains(scope.toLowerCase())) {
                        hasToBeExecuted = true;
                    }
                }



                if (hasToBeExecuted) {
                    if(report) {
                        writer.write(graphTest.getId());
                        writer.write(" - ");

                        Date date = new Date();
                        writer.write(formatter.format(date));
                        writer.write(" - ");
                    }
                    boolean passed = TestExecutor.executeTest(graphTest,graphFolder,inputFolder,expectedOutputFolder,tempFolder,snapBinFolder, reportFolderPath.getParent(), profiler);
                    if(report) {
                        Date date = new Date();
                        writer.write(formatter.format(date));
                        writer.write(" - ");
                        if (profiler) {
                            try {
                                // Moving profiling output to the report folder
                                Path perfGPT = Paths.get(tempFolder.resolve(graphTest.getId()).toString() + "_perf.csv");
                                Files.copy(perfGPT, reportFolderPath.resolve(perfGPT.getFileName()));
                                Path cpuplotPath = Paths.get(tempFolder.resolve(graphTest.getId()).toString() + "_perf_cpu_usage.png");
                                Files.copy(cpuplotPath, reportFolderPath.resolve(cpuplotPath.getFileName()));
                                Path memplotPath = Paths.get(tempFolder.resolve(graphTest.getId()).toString() + "_perf_memory_usage.png");
                                Files.copy(memplotPath, reportFolderPath.resolve(memplotPath.getFileName()));
                                Path ioplotPath = Paths.get(tempFolder.resolve(graphTest.getId()).toString() + "_perf_IO_usage.png");
                                Files.copy(ioplotPath, reportFolderPath.resolve(ioplotPath.getFileName()));
                            }catch (Exception e) {
                                System.out.println(String.format("Cannot copy performance: %s",e.getMessage()));
                            }
                        }
                        if(passed) {
                            writer.write("PASSED");
                        } else {
                            writer.write("FAILED");
                            success = false;
                            //copy  output product to report
                            if(!scope.toLowerCase().equals("release") && !scope.toLowerCase().equals("weekly") && !scope.toLowerCase().equals("daily") && !scope.toLowerCase().equals("regular")) {
                                for (Output output : graphTest.getOutputs()) {
                                    Collection<File> outputFiles = FileUtils.listFilesAndDirs(tempFolder.toFile(), new WildcardFileFilter(String.format("%s*", output.getOutputName())), new WildcardFileFilter(String.format("%s*", output.getOutputName())));
                                    for (File outputFile : outputFiles) {
                                        if (outputFile.toString().equals(tempFolder.toString())) {
                                            continue;
                                        }
                                        Files.copy(outputFile.toPath(), reportFolderPath.resolve(outputFile.getName()));
                                        if (outputFile.isDirectory()) {
                                            FileUtils.copyDirectory(outputFile, reportFolderPath.resolve(outputFile.getName()).toFile());
                                        }
                                    }
                                }
                            }

                            //copy output of gpt to report (perhaps it has been copied before, so try-catch)
                            try {
                                Path reportGPT = Paths.get(tempFolder.resolve(graphTest.getId()).toString() + "_gptOutput.txt");
                                Files.copy(reportGPT, reportFolderPath.resolve(reportGPT.getFileName()));
                             
                            } catch (Exception e) {
                                System.out.println(String.format("Cannot copy gptOutput: %s",e.getMessage()));
                            }

                            //copy output of content to report (perhaps it has been copied before, so try-catch)
                            try {
                                Path reportContent = Paths.get(tempFolder.resolve(graphTest.getId()).toString() + "_contentError.txt");
                                Files.copy(reportContent, reportFolderPath.resolve(reportContent.getFileName()));
                            } catch (Exception e) {
                                System.out.println(String.format("Cannot copy contentOutput: %s",e.getMessage()));
                            }

                        }
                        writer.write("\n");
                    }
                }
            }
        }

        if(writer != null) {
            writer.close();
        }

        if(!success) {
            System.exit(1);
        }
    }
}