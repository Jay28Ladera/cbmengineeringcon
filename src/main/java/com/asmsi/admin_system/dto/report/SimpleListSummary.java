package com.asmsi.admin_system.dto.report;

import java.util.List;

public record SimpleListSummary<T>(long total, List<T> visits) {}
