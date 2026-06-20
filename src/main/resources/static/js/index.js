const isAdmin = document.body.dataset.isAdmin === 'true';

let currentPage = 0;
let loading = false;
let hasMore = true;
let searchQuery = '';
let debounceTimer = null;

function createProductCard(product) {
    const template = document.getElementById('product-template');
    const clone = template.content.cloneNode(true);

    clone.querySelector('.product-image').src = product.imageUrl;
    clone.querySelector('.product-image').alt = product.name;
    clone.querySelector('.details-link').href = `/products/${product.id}`;
    clone.querySelector('.product-title').textContent = product.name;
    clone.querySelector('.product-description').textContent = product.description;
    clone.querySelector('.price').textContent =
        Number(product.price).toLocaleString('fa-IR') + ' تومان';

    if (isAdmin) {
        const actions = document.createElement('div');
        actions.className = 'admin-actions';

        const editBtn = document.createElement('a');
        editBtn.href = `/products/${product.id}/edit`;
        editBtn.textContent = '✏️ ویرایش';
        editBtn.className = 'btn-edit';

        const deleteBtn = document.createElement('button');
        deleteBtn.textContent = '🗑️ حذف';
        deleteBtn.className = 'btn-delete';
        deleteBtn.onclick = () => handleDelete(product.id);

        actions.appendChild(editBtn);
        actions.appendChild(deleteBtn);
        clone.querySelector('.extra-content').appendChild(actions);
    }

    return clone;
}

function handleDelete(id) {
    if (!confirm('مطمئنی می‌خوای این محصولِ زبان‌بسته رو به تاریخ بپیوندی؟ 🥺')) return;
    fetch(`/products/${id}/delete`, {method: 'POST'})
        .then(res => res.ok ? window.location.reload() : alert('حذف ناموفق بود!'));
}

function loadProducts() {
    if (loading || !hasMore) return;
    loading = true;
    document.getElementById('loader').style.display = 'block';

    const url = searchQuery
        ? `/api/products/search?q=${encodeURIComponent(searchQuery)}&page=${currentPage}`
        : `/api/products?page=${currentPage}`;

    fetch(url)
        .then(res => res.json())
        .then(data => {
            const container = document.getElementById('product-container');
            data.content.forEach(product => container.appendChild(createProductCard(product)));

            currentPage++;
            hasMore = !data.last;
            loading = false;
            document.getElementById('loader').style.display = 'none';
        });
}

function resetAndLoad() {
    currentPage = 0;
    hasMore = true;
    loading = false;
    document.getElementById('product-container').innerHTML = '';
    loadProducts();
}

const searchInput = document.getElementById('search-input');
const searchClear = document.getElementById('search-clear');

searchInput.addEventListener('input', () => {
    const val = searchInput.value.trim();
    searchClear.style.display = val ? 'flex' : 'none';

    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
        searchQuery = val;
        resetAndLoad();
    }, 300);
});

searchClear.addEventListener('click', () => {
    searchInput.value = '';
    searchClear.style.display = 'none';
    searchQuery = '';
    resetAndLoad();
    searchInput.focus();
});

window.addEventListener('scroll', () => {
    if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 500) {
        loadProducts();
    }
});

loadProducts();