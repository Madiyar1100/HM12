import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.*;

public class HiringProcessSimulation {
    enum RequestStatus {PENDING, APPROVED, REJECTED}
    enum CandidateStatus {APPLIED, REJECTED, INVITED, OFFERED, HIRED}
    static class VacancyRequest {
        final UUID id = UUID.randomUUID();
        final String position;
        final String department;
        RequestStatus status = RequestStatus.PENDING;
        VacancyRequest(String position, String department){ this.position = position; this.department = department; }
        public String toString(){ return "Vacancy["+id.toString().substring(0,8)+"] "+position+" ("+department+")"; }
    }
    static class Candidate {
        final UUID id = UUID.randomUUID();
        final String name;
        final Map<String,String> resume;
        CandidateStatus status = CandidateStatus.APPLIED;
        Candidate(String name, Map<String,String> resume){ this.name=name;this.resume=resume; }
        public String toString(){ return "Candidate["+id.toString().substring(0,8)+"] "+name; }
    }
    static class CompanyDatabase {
        final List<Map<String,String>> employees = Collections.synchronizedList(new ArrayList<>());
        void addEmployee(Map<String,String> record){ employees.add(record); }
        int size(){ return employees.size(); }
    }
    static class NotificationService {
        void notifyManager(VacancyRequest r, String message){ System.out.println(now()+" [Notify][Manager] "+r+" : "+message); }
        void notifyRequester(VacancyRequest r, String message){ System.out.println(now()+" [Notify][Requester] "+r+" : "+message); }
        void notifyCandidate(Candidate c, String message){ System.out.println(now()+" [Notify][Candidate] "+c+" : "+message); }
        void notifyIT(String message){ System.out.println(now()+" [Notify][IT] : "+message); }
    }
    static class HR {
        final NotificationService notifier;
        final CompanyDatabase db;
        HR(NotificationService notifier, CompanyDatabase db){ this.notifier=notifier;this.db=db; }
        boolean validateVacancy(VacancyRequest r){
            boolean ok = r.position != null && !r.position.isBlank() && r.department != null && !r.department.isBlank();
            System.out.println(now()+" [HR] Validation for "+r+" -> "+(ok?"APPROVED":"REJECTED"));
            return ok;
        }
        boolean screenResume(Candidate c){
            boolean ok = c.resume.containsKey("experience") && Integer.parseInt(c.resume.getOrDefault("experience","0"))>=2;
            System.out.println(now()+" [HR] Screening "+c+" -> "+(ok?"PASS":"FAIL"));
            return ok;
        }
        void onboard(Candidate c){
            Map<String,String> rec = new HashMap<>();
            rec.put("id",c.id.toString());
            rec.put("name",c.name);
            rec.putAll(c.resume);
            db.addEmployee(rec);
            System.out.println(now()+" [HR] Onboarded "+c);
        }
    }
    static class Manager {
        final NotificationService notifier;
        Manager(NotificationService notifier){ this.notifier=notifier; }
        Optional<VacancyRequest> createRequest(String position, String department){
            VacancyRequest r = new VacancyRequest(position, department);
            System.out.println(now()+" [Manager] Created "+r);
            return Optional.of(r);
        }
        boolean approveVacancy(VacancyRequest r){
            System.out.println(now()+" [Manager] Approving "+r);
            return true;
        }
        boolean technicalInterview(Candidate c){
            boolean ok = c.resume.getOrDefault("skills","").contains("java");
            System.out.println(now()+" [Manager] Technical interview for "+c+" -> "+(ok?"PASS":"FAIL"));
            return ok;
        }
    }
    static class PublishingService {
        void publishVacancy(VacancyRequest r){
            System.out.println(now()+" [System] Published vacancy: "+r);
        }
    }
    static class IT {
        final NotificationService notifier;
        IT(NotificationService notifier){ this.notifier = notifier; }
        void prepareWorkspace(Candidate c){
            System.out.println(now()+" [IT] Preparing workspace for "+c);
            try{ Thread.sleep(200); }catch(Exception e){}
            System.out.println(now()+" [IT] Workspace ready for "+c);
        }
    }
    static class InterviewProcess {
        final HR hr;
        final Manager manager;
        final NotificationService notifier;
        InterviewProcess(HR hr, Manager manager, NotificationService notifier){ this.hr=hr;this.manager=manager;this.notifier=notifier; }
        boolean conduct(Candidate c){
            System.out.println(now()+" [Interview] HR initial interview with "+c);
            boolean hrPass = hr.screenResume(c);
            if(!hrPass){ notifier.notifyCandidate(c,"К сожалению, вы не прошли первичный отбор."); c.status=CandidateStatus.REJECTED; return false; }
            c.status = CandidateStatus.INVITED;
            notifier.notifyCandidate(c,"Приглашение на техническое собеседование.");
            System.out.println(now()+" [Interview] Manager technical interview with "+c);
            boolean techPass = manager.technicalInterview(c);
            if(!techPass){ notifier.notifyCandidate(c,"К сожалению, вы не прошли техническое собеседование."); c.status=CandidateStatus.REJECTED; return false; }
            c.status = CandidateStatus.OFFERED;
            notifier.notifyCandidate(c,"Вам предложен оффер.");
            return true;
        }
    }
    static String now(){ return LocalTime.now().withNano(0).toString(); }
    public static void main(String[] args) throws Exception{
        NotificationService notifier = new NotificationService();
        CompanyDatabase db = new CompanyDatabase();
        HR hr = new HR(notifier, db);
        Manager manager = new Manager(notifier);
        PublishingService pub = new PublishingService();
        IT it = new IT(notifier);
        InterviewProcess interview = new InterviewProcess(hr, manager, notifier);
        ExecutorService exec = Executors.newFixedThreadPool(8);
        List<Candidate> candidatePool = Collections.synchronizedList(new ArrayList<>());
        Optional<VacancyRequest> maybeRequest = manager.createRequest("Software Engineer", "R&D");
        if(maybeRequest.isEmpty()) return;
        VacancyRequest request = maybeRequest.get();
        notifier.notifyRequester(request,"Заявка создана и отправлена в HR.");
        boolean managerApproved = manager.approveVacancy(request);
        if(!managerApproved){ request.status = RequestStatus.REJECTED; notifier.notifyRequester(request,"Заявка отклонена руководителем."); return; }
        boolean hrApproved = hr.validateVacancy(request);
        if(!hrApproved){ request.status = RequestStatus.REJECTED; notifier.notifyRequester(request,"Заявка требует доработки."); return; }
        request.status = RequestStatus.APPROVED;
        notifier.notifyManager(request,"Заявка утверждена HR.");
        pub.publishVacancy(request);
        int simulatedCandidates = 6;
        CountDownLatch applicationsLatch = new CountDownLatch(simulatedCandidates);
        for(int i=1;i<=simulatedCandidates;i++){
            final int idx = i;
            exec.submit(()->{
                try{
                    Thread.sleep(100L*idx);
                    Map<String,String> resume = new HashMap<>();
                    resume.put("experience", String.valueOf((idx%4)+1));
                    resume.put("skills", idx%2==0? "java,sql" : "python,js");
                    resume.put("education","Bachelor");
                    Candidate c = new Candidate("Cand"+idx, resume);
                    candidatePool.add(c);
                    System.out.println(now()+" [System] Received application from "+c);
                }catch(Exception e){}
                finally{ applicationsLatch.countDown(); }
            });
        }
        applicationsLatch.await();
        System.out.println(now()+" [System] All applications received: "+candidatePool.size());
        List<CompletableFuture<Void>> interviewFutures = new ArrayList<>();
        for(Candidate c : new ArrayList<>(candidatePool)){
            CompletableFuture<Void> f = CompletableFuture.runAsync(()->{
                boolean passed = interview.conduct(c);
                if(passed){
                    boolean accepted = simulateCandidateDecision(c);
                    if(accepted){
                        c.status = CandidateStatus.HIRED;
                        hr.onboard(c);
                        it.prepareWorkspace(c);
                        notifier.notifyIT("Настройте учетную запись для "+c.name);
                    } else {
                        c.status = CandidateStatus.REJECTED;
                        notifier.notifyCandidate(c,"К сожалению, вы отказались от оффера.");
                    }
                }
            }, exec);
            interviewFutures.add(f);
        }
        CompletableFuture.allOf(interviewFutures.toArray(new CompletableFuture[0])).join();
        System.out.println(now()+" [System] Hiring process finished. Total employees: "+db.size());
        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);
        provideAnalysisAndRecommendations();
    }
    static boolean simulateCandidateDecision(Candidate c){
        try{ Thread.sleep(50); }catch(Exception e){}
        boolean decision = c.resume.getOrDefault("experience","0").equals("3") || c.resume.getOrDefault("skills","").contains("java");
        System.out.println(now()+" [Decision] Candidate "+c+" accepts offer: "+decision);
        return decision;
    }
    static void provideAnalysisAndRecommendations(){
        System.out.println("\n--- Анализ процесса ---");
        System.out.println("1. Этапы: создание заявки, валидация HR, публикация, прием заявок, первичный скрининг, тех. интервью, оффер, онбординг, настройка рабочего места.");
        System.out.println("2. Ветвления: отклонение заявки, отклонение кандидата на скрининге, провал тех.собеседования, отказ кандидата от оффера.");
        System.out.println("3. Параллельность: публикация вакансии и параллельный прием заявок; параллельное проведение интервью.");
        System.out.println("\n--- Рекомендации ---");
        System.out.println("1. Автоматизировать первичный скрининг резюме (по ключевым навыкам и опыту).");
        System.out.println("2. Внедрить очередь заданий для IT для предсказуемой настройки рабочих мест.");
        System.out.println("3. Использовать асинхронные уведомления и шаблоны офферов для ускорения отклика кандидатов.");
    }
}
