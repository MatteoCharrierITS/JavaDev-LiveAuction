document.addEventListener('DOMContentLoaded', () => {
    const loading = document.getElementById('catalog-loading');
    const showLoading = () => { loading.hidden = false; };
    document.getElementById('catalog-filters').addEventListener('submit', showLoading);
    document.querySelectorAll('.pagination a').forEach(link => link.addEventListener('click', showLoading));
    window.addEventListener('pageshow', () => { loading.hidden = true; });
});
