import java.util.*;
import java.util.concurrent.*;
import java.time.*;

class BookingRequest {
    UUID id = UUID.randomUUID();
    String clientName;
    LocalDate date;
    String venue;
    int price;
    BookingRequest(String clientName, LocalDate date, String venue, int price){ this.clientName=clientName;this.date=date;this.venue=venue;this.price=price; }
    public String toString(){ return "Booking["+id.toString().substring(0,8)+"] "+clientName+" @"+venue+" "+date; }
}

class Booking {
    UUID id = UUID.randomUUID();
    BookingRequest req;
    boolean confirmed=false;
    Booking(BookingRequest req){ this.req=req; }
    public String toString(){ return "ConfirmedBooking["+id.toString().substring(0,8)+"] for "+req.clientName; }
}

class PaymentResult {
    boolean success;
    String transactionId;
    PaymentResult(boolean success, String transactionId){ this.success=success;this.transactionId=transactionId; }
}

class VenueAvailabilityService {
    Map<String, Set<LocalDate>> occupied = new ConcurrentHashMap<>();
    boolean isAvailable(String venue, LocalDate date){ return !occupied.getOrDefault(venue, Collections.emptySet()).contains(date); }
    void reserve(String venue, LocalDate date){ occupied.computeIfAbsent(venue,k->ConcurrentHashMap.newKeySet()).add(date); }
}

class PaymentGateway {
    Random rnd = new Random();
    PaymentResult charge(String client, int amount){
        boolean ok = rnd.nextInt(100) < 80;
        return new PaymentResult(ok, ok? UUID.randomUUID().toString().substring(0,8) : null);
    }
}

class Admin {
    String name;
    Admin(String name){ this.name=name; }
    List<String> prepareTasks(Booking b){
        List<String> tasks = new ArrayList<>();
        tasks.add("Decorations");
        tasks.add("Catering");
        tasks.add("Sound equipment");
        tasks.add("Security");
        System.out.println(now()+" [Admin] Prepared tasks for "+b);
        return tasks;
    }
}

class Contractor {
    String name;
    Contractor(String name){ this.name=name; }
    boolean performTask(String task){
        try{ Thread.sleep(100 + new Random().nextInt(200)); }catch(Exception e){}
        boolean ok = new Random().nextInt(100) < 90;
        System.out.println(now()+" [Contractor "+name+"] Task '"+task+"' -> "+(ok?"DONE":"FAILED"));
        return ok;
    }
}

class Manager {
    String name;
    Manager(String name){ this.name=name; }
    void receiveReport(String report){ System.out.println(now()+" [Manager] Received report: "+report); }
}

class SystemService {
    VenueAvailabilityService venueService = new VenueAvailabilityService();
    PaymentGateway paymentGateway = new PaymentGateway();
    Admin admin = new Admin("VenueAdmin");
    List<Contractor> contractors = Arrays.asList(new Contractor("C1"), new Contractor("C2"), new Contractor("C3"));
    Manager manager = new Manager("OpsManager");
    ExecutorService exec = Executors.newFixedThreadPool(6);
    Optional<Booking> processBooking(BookingRequest req){
        System.out.println(now()+" [System] Received request: "+req);
        boolean available = venueService.isAvailable(req.venue, req.date);
        if(!available){
            System.out.println(now()+" [System] Venue not available. Propose alternatives to "+req.clientName);
            return Optional.empty();
        }
        System.out.println(now()+" [System] Venue available. Sending price to client: "+req.price);
        PaymentResult pay = paymentGateway.charge(req.clientName, req.price);
        if(!pay.success){
            System.out.println(now()+" [PaymentGateway] Payment failed for "+req.clientName);
            return Optional.empty();
        }
        System.out.println(now()+" [PaymentGateway] Payment succeeded tx="+pay.transactionId);
        venueService.reserve(req.venue, req.date);
        Booking b = new Booking(req);
        b.confirmed = true;
        System.out.println(now()+" [System] Booking confirmed: "+b);
        notifyAdminAndOrganize(b);
        return Optional.of(b);
    }
    void notifyAdminAndOrganize(Booking b){
        System.out.println(now()+" [System] Notifying admin about booking "+b);
        List<String> tasks = admin.prepareTasks(b);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for(int i=0;i<tasks.size();i++){
            final String task = tasks.get(i);
            final Contractor c = contractors.get(i % contractors.size());
            CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(()-> c.performTask(task), exec);
            futures.add(f);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRunAsync(()->{
            long successes = futures.stream().filter(f->{
                try{ return f.get(); }catch(Exception e){ return false; }
            }).count();
            String report = "Tasks completed: "+successes+"/"+tasks.size();
            System.out.println(now()+" [System] All contractor tasks finished. "+report);
            manager.receiveReport(report);
        }, exec);
    }
    void collectFeedbackAndReport(Booking b){
        System.out.println(now()+" [System] Event finished for "+b.req.clientName+". Asking for feedback.");
        List<Integer> ratings = Arrays.asList(5,4,5,3);
        double avg = ratings.stream().mapToInt(i->i).average().orElse(0.0);
        String report = "Event "+b.id.toString().substring(0,8)+" feedback avg="+avg;
        System.out.println(now()+" [System] Collected feedback. "+report);
        manager.receiveReport(report);
    }
    void shutdown(){ exec.shutdown(); try{ exec.awaitTermination(3, TimeUnit.SECONDS); }catch(Exception e){} }
    static String now(){ return LocalTime.now().withNano(0).toString(); }
}

public class EventBookingSimulation {
    public static void main(String[] args) throws Exception{
        SystemService sys = new SystemService();
        BookingRequest req1 = new BookingRequest("Alice", LocalDate.now().plusDays(10), "GrandHall", 1000);
        BookingRequest req2 = new BookingRequest("Bob", LocalDate.now().plusDays(10), "GrandHall", 1200);
        Optional<Booking> b1 = sys.processBooking(req1);
        Optional<Booking> b2 = sys.processBooking(req2);
        if(b1.isPresent()){
            Thread.sleep(1000);
            sys.collectFeedbackAndReport(b1.get());
        }
        if(b2.isPresent()){
            Thread.sleep(1000);
            sys.collectFeedbackAndReport(b2.get());
        }
        sys.shutdown();
    }
}
