import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections.*;
import java.util.concurrent.*;
import java.util.*;
import java.lang.*;

public class AtmosphericTemperatureModule {

private static final int numberSensors = 8;
private static final int numberHours = 1;
private static final int minTemp = -100;
private static final int maxTemp = 70;

public static AtomicBoolean reportBeingGenerated = new AtomicBoolean(false);
public static AtomicBoolean intervalBeingCalculated = new AtomicBoolean(false);
public static AtomicBoolean allHoursComplete = new AtomicBoolean(false);

public static PriorityBlockingQueue<Integer> sharedMaxMemory;
public static PriorityBlockingQueue<Integer> sharedMinMemory;

public static AtomicInteger biggestDiff = new AtomicInteger(0);
public static AtomicInteger minTempInterval = new AtomicInteger(Integer.MAX_VALUE);
public static AtomicInteger maxTempInterval = new AtomicInteger(Integer.MIN_VALUE);

public static AtomicInteger curIntervalStart = new AtomicInteger(0);
public static AtomicInteger curIntervalEnd = new AtomicInteger(0);
public static AtomicInteger intervalStart = new AtomicInteger(0);
public static AtomicInteger intervalEnd = new AtomicInteger(0);

public static long startTime;

public static void main(String args[]) {

        startTime = (int) System.currentTimeMillis() / 1000;

        // instantiate shared memory storage
        sharedMaxMemory = new PriorityBlockingQueue<Integer>
                                  (480, Collections.reverseOrder());
        sharedMinMemory = new PriorityBlockingQueue<Integer>();

        // initialize temperature orchestrator
        Thread orchestrator = new Thread(new ModuleOrchestrator());
        // initialize all sensor threads
        ArrayList<Thread> sensors = new ArrayList<Thread>(numberSensors);
        for (int i = 0; i < numberSensors; i++) {
                sensors.add(new Thread(new SensorThread(i, sharedMaxMemory,
                                                        sharedMinMemory)));
        }
        // initialize temp difference thread
        Thread tempDiff = new Thread(new TempIntervalThread());

        // begin the temperature readings
        try {
                orchestrator.start();
                for (int i = 0; i < numberSensors; i++) {
                        sensors.get(i).start();
                }
                tempDiff.start();
        }
        catch (Exception e) {
                e.printStackTrace();
        }

        // wait for threads to complete
        try {
                orchestrator.join();
                for (int i = 0; i < numberSensors; i++) {
                        sensors.get(i).join();
                }
                tempDiff.join();
        }
        catch (Exception e) {
                e.printStackTrace();
        }
        return;
}

static class ModuleOrchestrator implements Runnable {
private AtomicInteger numHoursComplete = new AtomicInteger(0);

@Override
public void run() {
        try {
                // until all hours complete
                while (numHoursComplete.get() < numberHours) {
                        System.out.println("Gathering temperatures...");
                        // wait every 20 seconds to generate report
                        Thread.sleep(1000*20);
                        generateReport();
                        numHoursComplete.getAndIncrement();
                }
                allHoursComplete.set(true);
        }
        catch (Exception e) {
                e.printStackTrace();
        }
}

public void generateReport() {
        // might have to add if / else here in case this CAS fails
        reportBeingGenerated.compareAndSet(false, true);

        System.out.println("------- Start Report -------");

        // generate report
        System.out.println("5 Lowest Temps:");
        for (int i = 0; i < 5; i++) {
                try {
                        System.out.print(sharedMinMemory.take() + "F, ");
                }
                catch (Exception e) {
                        e.printStackTrace();
                }

        }

        System.out.println("\n5 Highest Temps:");
        for (int i = 0; i < 5; i++) {
                try {
                        System.out.print(sharedMaxMemory.take() + "F, ");
                }
                catch (Exception e) {
                        e.printStackTrace();
                }
        }
        System.out.println("\nGreatest Temperature Difference: \n" +
                           biggestDiff.get() + "F which occured between second: "
                           + intervalStart.get() + " and second: " +
                           intervalEnd.get());

        // clear data structure
        sharedMaxMemory.clear();
        sharedMinMemory.clear();

        // reset temperature diff calculations for this new hour
        biggestDiff.set(0);
        minTempInterval.set(Integer.MAX_VALUE);
        maxTempInterval.set(Integer.MIN_VALUE);
        intervalStart.set(0);
        intervalEnd.set(0);

        System.out.println("------- End Report -------");
        // mark end of report generation, so other threads can resume temp readings
        reportBeingGenerated.compareAndSet(true, false);
}
}

static class TempIntervalThread implements Runnable {
@Override
public void run() {
        try {
                while (!allHoursComplete.get()) {
                        // spin while report is being generated so not to interfere
                        while (reportBeingGenerated.get()) {};
                        // every 10 seconds
                        Thread.sleep(1000*10);
                        recordTemperatureDifference();
                }
        }
        catch (Exception e) {
                e.printStackTrace();
        }
}

public void recordTemperatureDifference() {
        intervalBeingCalculated.compareAndSet(false, true);
        // get this 10 minute intervals biggest temp difference
        int currentDiff = maxTempInterval.get() - minTempInterval.get();
        // compare to this hours biggest temp difference
        if (biggestDiff.get() < currentDiff) {
                biggestDiff.set(currentDiff);
                intervalStart.set(curIntervalStart.get());
                intervalEnd.set(curIntervalEnd.get());
        }
        // reset for next 10 minute interval
        maxTempInterval.set(Integer.MIN_VALUE);
        minTempInterval.set(Integer.MAX_VALUE);
        curIntervalEnd.set(0);
        curIntervalStart.set(0);

        intervalBeingCalculated.compareAndSet(true, false);
}
}

static class SensorThread implements Runnable {
private int sensorId;
private PriorityBlockingQueue<Integer> sharedMaxMemory;
private PriorityBlockingQueue<Integer> sharedMinMemory;
public SensorThread(int id, PriorityBlockingQueue<Integer> max,
                    PriorityBlockingQueue<Integer> min) {
        this.sensorId = id;
        this.sharedMaxMemory = max;
        this.sharedMinMemory = min;
}

@Override
public void run() {
        try {
                while (!allHoursComplete.get()) {
                        // all threads should spin while report is being generated
                        // or interval being calculated
                        while (reportBeingGenerated.get() ||
                               intervalBeingCalculated.get()) {};
                        // every 1 second, this thread should read a temperature
                        Thread.sleep(1000*1);
                        getTemperatureReading();
                }
        }
        catch (Exception e) {
                e.printStackTrace();
        }
}

public void getTemperatureReading() {
        // wait if report is generated
        while (reportBeingGenerated.get() || intervalBeingCalculated.get()) {};
        // get reading
        int temp = ThreadLocalRandom.current().nextInt(minTemp, maxTemp + 1);
        // write to shared memory space
        sharedMaxMemory.add(temp);
        sharedMinMemory.add(temp);

        // update our interval temperatures if needed
        long endTime = (int) System.currentTimeMillis() / 1000;
        if (temp < minTempInterval.get()) {
                minTempInterval.set(temp);
                curIntervalStart.set((int)(endTime - startTime) % 60);
        }

        if (temp > maxTempInterval.get()) {
                maxTempInterval.set(temp);
                curIntervalEnd.set((int)(endTime - startTime) % 60);
        }
}
}
}
