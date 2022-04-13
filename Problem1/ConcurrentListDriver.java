import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

public class ConcurrentListDriver {
private static final int numPresents = 500000;
private static final int numThreads = 4;

// create variables to track progress of gift adding and removing
public static AtomicInteger addCount = new AtomicInteger();
public static AtomicInteger removeCount = new AtomicInteger();

public static void main(String args[]) {
        System.out.println("-------------------- Simulation started --------------------");
        // initialize a set to keep track of what gifts have been processed from
        // the bag
        ConcurrentHashMap<Integer, Integer> giftBag = new ConcurrentHashMap<>(numPresents);
        for (int i = 1; i <= numPresents; i++) {
                // we only care about the key, put a sentinel number for value
                giftBag.put(i, 0);
        }

        // initialize our concurrent chain of gifts and servants
        ConcurrentLinkedList chain = new ConcurrentLinkedList<Integer>();
        ArrayList<Thread> servants = new ArrayList<Thread>(numThreads);

        // initialize servant threads
        for (int i = 0; i < numThreads; i++) {
                ServantThread servant = new ServantThread(i+1, chain, giftBag);
                Thread servantThread = new Thread(servant);
                servants.add(servantThread);
        }
        // start servant threads
        for (int i = 0; i < numThreads; i++) {
                servants.get(i).start();
        }
        // wait for threads to complete tasks
        for (int i = 0; i < numThreads; i++) {
                try {
                        servants.get(i).join();
                }
                catch (Exception e) {
                        System.out.println(e);
                }
        }
        // terminate threads
        try {
                for (int i = 0; i < numThreads; i++) {
                        servants.get(i).interrupt();
                }
        }
        catch (Exception e) {
                System.out.println(e);
        }
        System.out.println("-------------------- Simulation complete --------------------");
        return;
}

// this class represents the servant thread and the actions it will take
static class ServantThread implements Runnable {

public int servantId;
private ConcurrentLinkedList<Integer> list;
private ConcurrentHashMap<Integer, Integer> giftBag;
public ServantThread(int id, ConcurrentLinkedList<Integer> list,
                     ConcurrentHashMap<Integer, Integer> giftBag) {
        this.servantId = id;
        this.list = list;
        this.giftBag = giftBag;
}

@Override
public void run() {
        try {
                Random random = new Random();
                while (!checkCount(true) || !checkCount(false)) {
                        // generate a random action (btwn 1-3) for this servant
                        int action = random.nextInt(3);
                        if (action == 0 && !checkCount(true)) {
                                addGiftToChain();
                        }
                        else if (action == 1 && !checkCount(false)) {
                                removeGiftFromChain();
                        }
                        else {
                                if(findGiftOnChain()) return;
                        }
                }
                return;
        }
        catch (Exception e) {
                e.printStackTrace();
        }
}

// returns false if there are still gifts to be added / removed
public boolean checkCount(boolean add) {
        if (add) {
                return (addCount.get() >= numPresents);
        }
        else {
                return (removeCount.get() >= numPresents);
        }
}

public void addGiftToChain() {
        // simulate grabbing a random gift from gift bag that hasn't been processed
        int giftNum = ThreadLocalRandom.current().nextInt(1, numPresents + 1);
        // while this gift is marked (processed), grab a new gift
        while (giftBag.get(giftNum) == -1) {
                // if another thread has completed the adding gift work, return
                if (checkCount(true)) {
                  return;
                }
                System.out.println("Gift ID: " + giftNum + " has already been processed, finding new gift...");
                giftNum = ThreadLocalRandom.current().nextInt(1, numPresents + 1);
        }

        // attempt to add this gift to the chain
        if (this.list.add(giftNum)) {
                System.out.println("Gift ID: " + giftNum + " added to chain");
                // if successful, increment addCount
                addCount.getAndIncrement();
                // replace with sentinel value to process this node as marked
                this.giftBag.replace(giftNum, 0, -1);
        }
        else {
          System.out.println("Couldn't add Gift ID: " + giftNum + ", already added!");
        }
        return;
}

public void removeGiftFromChain() {
        // simulate grabbing a random gift from chain
        int giftNum = ThreadLocalRandom.current().nextInt(1, numPresents + 1);
        // attempt to remove this gift from the chain
        if (this.list.remove(giftNum)) {
                System.out.println("Gift ID: " + giftNum + " removed from chain");
                // if successful, increment removeCount
                removeCount.getAndIncrement();
        }
        else {
          System.out.println("Couldn't remove Gift ID: " + giftNum + ", already removed!");
        }
        return;
}

public boolean findGiftOnChain() {
        int giftNum = ThreadLocalRandom.current().nextInt(1, numPresents);
        if (this.list.contains(giftNum)) {
                System.out.println("Gift ID: " + giftNum + " found in chain");
        }
        else {
                System.out.println("Gift ID: " + giftNum + " not found in chain");
        }
        // return if another thread has finished all processes regarding the
        // bag and the chain
        return (checkCount(true) && checkCount(false));
}
}
}
