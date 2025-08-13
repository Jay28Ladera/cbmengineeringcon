package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.VisitingDayRequest;
import com.asmsi.admin_system.entity.VisitingDayRecord;
import com.asmsi.admin_system.repository.VisitingDayRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class VisitingDayService {

    private final VisitingDayRepository repo;

    public VisitingDayService(VisitingDayRepository repo) {
        this.repo = repo;
    }

    public void save(VisitingDayRequest req) {
        // defaults / safety
        LocalDate date = req.getDate() != null ? req.getDate() : LocalDate.now();

        LocalTime timeIn = null;
        if (req.getTimeIn() != null && !req.getTimeIn().isBlank()) {
            try {
                timeIn = LocalTime.parse(req.getTimeIn()); // expects "HH:mm:ss"
            } catch (Exception ignored) {
                timeIn = LocalTime.now();
            }
        } else {
            timeIn = LocalTime.now();
        }

        int adult = req.getAdultVisitor() != null ? Math.max(0, req.getAdultVisitor()) : 0;
        int kid   = req.getKidVisitor()   != null ? Math.max(0, req.getKidVisitor())   : 0;
        int total = req.getTotalVisitors() != null ? Math.max(0, req.getTotalVisitors()) : (adult + kid);

        VisitingDayRecord record = VisitingDayRecord.builder()
                .idNumber(req.getIdNumber())
                .lrn(req.getLrn())
                .name(req.getName())
                .campus(req.getCampus())
                .familySaint(req.getFamilySaint())
                .batchNumber(req.getBatchNumber())
                .imageUrl(req.getImageUrl())
                .address(req.getAddress())

                .date(date)
                .timeIn(timeIn)

                .adultVisitor(adult)
                .kidVisitor(kid)
                .totalVisitors(total)

                .account(req.getAccount())
                .build();

        repo.save(record);
    }

    public List<VisitingDayRecord> latest(int limit) {
        // simple version: get top 50 and sublist to limit
        List<VisitingDayRecord> top = repo.findTop50ByOrderByCreatedAtDesc();
        return top.size() <= limit ? top : top.subList(0, limit);
    }
}
