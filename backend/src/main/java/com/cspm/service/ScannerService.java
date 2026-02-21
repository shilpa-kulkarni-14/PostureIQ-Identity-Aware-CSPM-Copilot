package com.cspm.service;

import com.cspm.model.ScanResult;

import java.util.List;
import java.util.Optional;

public interface ScannerService {

    ScanResult runScan();

    Optional<ScanResult> getScanResult(String scanId);

    List<ScanResult> getAllScans();
}
