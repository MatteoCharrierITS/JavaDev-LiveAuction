package it.esercitazione.liveauction.consumer.admin;

import java.util.List;

public record AdminProductPage(List<AdminProduct> content, int page, int size, long totalElements, int totalPages) {}
