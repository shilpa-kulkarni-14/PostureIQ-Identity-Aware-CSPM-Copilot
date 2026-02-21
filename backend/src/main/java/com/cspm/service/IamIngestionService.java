package com.cspm.service;

import com.cspm.model.IamIdentity;

import java.util.List;

public interface IamIngestionService {

    List<IamIdentity> ingestIdentities();
}
