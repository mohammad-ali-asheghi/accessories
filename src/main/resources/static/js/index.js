/**
 * Mini-App Index JavaScript
 * Dynamic catalog, live search, categories filter, infinite scroll, and modals
 */

const isAdmin = document.body.dataset.isAdmin === 'true';

let currentPage = 0;
const pageSize = 12;
let loading = false;
let hasMore = true;
let searchQuery = '';
let selectedCategory = '';
let debounceTimer = null;
let pendingDeleteId = null;

// Initialize on DOM load
document.addEventListener('DOMContentLoaded', () => {
    initSearch();
    initCategories();
    initFlashToast();

    const container = document.getElementById('product-container');
    const existingCards = container ? container.querySelectorAll('.product-card').length : 0;
    if (existingCards > 0) {
        // Page 0 was already rendered by server! Next page for infinite scroll is 1
        currentPage = 1;
    } else {
        // No server-side cards, fetch page 0
        currentPage = 0;
        loadProducts();
    }

    // Infinite scroll
    window.addEventListener('scroll', handleScroll, { passive: true });
});

function handleScroll() {
    if (loading || !hasMore) return;
    const scrollPosition = window.innerHeight + window.scrollY;
    const threshold = document.documentElement.offsetHeight - 600;
    if (scrollPosition >= threshold) {
        loadProducts();
    }
}

function initSearch() {
    const searchInput = document.getElementById('search-input');
    const searchClear = document.getElementById('search-clear');
    if (!searchInput) return;

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
}

function initCategories() {
    const chips = document.querySelectorAll('.category-chip');
    chips.forEach(chip => {
        chip.addEventListener('click', () => {
            chips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            selectedCategory = chip.dataset.category || '';
            resetAndLoad();
        });
    });
}

function initFlashToast() {
    const flashToast = document.getElementById('flash-toast');
    if (flashToast) {
        setTimeout(() => {
            flashToast.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
            flashToast.style.opacity = '0';
            flashToast.style.transform = 'translateY(-16px)';
            setTimeout(() => flashToast.remove(), 500);
        }, 4000);
    }
}

function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    const iconSvg = type === 'success'
        ? `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>`
        : `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>`;

    toast.innerHTML = `${iconSvg}<span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.transition = 'opacity 0.4s ease, transform 0.4s ease';
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px)';
        setTimeout(() => toast.remove(), 400);
    }, 3500);
}

function resetAndLoad() {
    currentPage = 0;
    hasMore = true;
    loading = false;
    const container = document.getElementById('product-container');
    if (container) container.innerHTML = '';
    const emptyState = document.getElementById('empty-state');
    if (emptyState) emptyState.style.display = 'none';
    loadProducts();
}

function resetFilters() {
    const searchInput = document.getElementById('search-input');
    const searchClear = document.getElementById('search-clear');
    if (searchInput) searchInput.value = '';
    if (searchClear) searchClear.style.display = 'none';
    searchQuery = '';
    selectedCategory = '';

    const chips = document.querySelectorAll('.category-chip');
    chips.forEach(c => {
        if (!c.dataset.category) c.classList.add('active');
        else c.classList.remove('active');
    });

    resetAndLoad();
}

function loadProducts() {
    if (loading || !hasMore) return;
    loading = true;

    const loader = document.getElementById('loader');
    if (loader) loader.style.display = 'block';

    const params = new URLSearchParams();
    params.set('page', currentPage);
    params.set('size', pageSize);
    if (searchQuery) params.set('q', searchQuery);
    if (selectedCategory) params.set('category', selectedCategory);

    const endpoint = searchQuery ? `/api/products/search?${params.toString()}` : `/api/products?${params.toString()}`;

    fetch(endpoint)
        .then(res => {
            if (!res.ok) throw new Error('خطا در دریافت لیست محصولات');
            return res.json();
        })
        .then(data => {
            const container = document.getElementById('product-container');
            const emptyState = document.getElementById('empty-state');

            if (currentPage === 0 && (!data.content || data.content.length === 0)) {
                if (emptyState) emptyState.style.display = 'block';
            } else if (emptyState) {
                emptyState.style.display = 'none';
            }

            if (data.content && data.content.length > 0) {
                data.content.forEach(product => {
                    const card = createProductCard(product);
                    container.appendChild(card);
                });
            }

            currentPage++;
            hasMore = !data.last;
        })
        .catch(err => {
            console.error('Fetch error:', err);
            showToast('خطا در بارگذاری محصولات', 'error');
        })
        .finally(() => {
            loading = false;
            if (loader) loader.style.display = 'none';
        });
}

function createProductCard(product) {
    const template = document.getElementById('product-template');
    const clone = template.content.cloneNode(true);
    const cardEl = clone.querySelector('.product-card');
    cardEl.dataset.id = product.id;

    // Image
    const imgEl = clone.querySelector('.product-image');
    imgEl.src = product.imageUrl || '/icon.png';
    imgEl.alt = product.name || 'محصول';
    imgEl.onerror = () => { imgEl.src = '/icon.png'; };

    // Details links
    clone.querySelectorAll('.details-link').forEach(link => {
        link.href = `/products/${product.id}`;
    });

    // Title & Desc
    clone.querySelector('.product-title').textContent = product.name || 'بدون نام';
    const descEl = clone.querySelector('.product-desc');
    descEl.textContent = product.description || '';

    // Stock Badge
    const stockBadge = clone.querySelector('.stock-badge');
    const inStock = product.inStock === true || product.inStock === 'true';
    if (inStock) {
        stockBadge.className = 'stock-badge badge badge-emerald';
        stockBadge.innerHTML = '<span class="pulse-dot"></span><span>موجود</span>';
    } else {
        stockBadge.className = 'stock-badge badge badge-rose';
        stockBadge.innerHTML = '<span>ناموجود</span>';
    }

    // Category Tag
    const catTag = clone.querySelector('.category-tag');
    if (product.category && product.category.trim()) {
        catTag.textContent = product.category;
        catTag.style.display = 'inline-flex';
    }

    // Price
    const priceVal = clone.querySelector('.price-value');
    if (product.price != null && product.price > 0) {
        priceVal.textContent = Number(product.price).toLocaleString('fa-IR') + ' تومان';
    } else {
        priceVal.textContent = 'تماس بگیرید';
        priceVal.style.color = 'var(--text-sky)';
    }

    // Admin Controls
    if (isAdmin) {
        const adminBar = document.createElement('div');
        adminBar.className = 'card-admin-bar';

        const editBtn = document.createElement('a');
        editBtn.href = `/products/${product.id}/edit`;
        editBtn.className = 'btn-admin-edit';
        editBtn.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
            </svg>
            ویرایش
        `;

        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'btn-admin-delete';
        deleteBtn.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"></polyline>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
            حذف
        `;
        deleteBtn.onclick = (e) => {
            e.preventDefault();
            e.stopPropagation();
            openDeleteModal(product.id);
        };

        adminBar.appendChild(editBtn);
        adminBar.appendChild(deleteBtn);
        cardEl.appendChild(adminBar);
    }

    return clone;
}

// Contact Modal Handling
function openContactModal() {
    const modal = document.getElementById('contact-modal');
    if (modal) modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeContactModal(event) {
    if (event && event.target && !event.target.classList.contains('modal-overlay') && !event.target.classList.contains('modal-close-btn')) {
        return;
    }
    const modal = document.getElementById('contact-modal');
    if (modal) modal.classList.remove('active');
    document.body.style.overflow = '';
}

// Delete Confirmation Modal
function openDeleteModal(id) {
    pendingDeleteId = id;
    const modal = document.getElementById('delete-modal');
    if (modal) modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeDeleteModal(event) {
    if (event && event.target && !event.target.classList.contains('modal-overlay') && event.target.tagName !== 'BUTTON') {
        return;
    }
    pendingDeleteId = null;
    const modal = document.getElementById('delete-modal');
    if (modal) modal.classList.remove('active');
    document.body.style.overflow = '';
}

document.addEventListener('DOMContentLoaded', () => {
    const confirmBtn = document.getElementById('confirm-delete-btn');
    if (confirmBtn) {
        confirmBtn.addEventListener('click', () => {
            if (!pendingDeleteId) return;

            confirmBtn.disabled = true;
            confirmBtn.textContent = 'در حال حذف...';

            fetch(`/products/${pendingDeleteId}/delete`, { method: 'POST' })
                .then(res => {
                    if (!res.ok) throw new Error('خطا در حذف محصول');
                    return res.json();
                })
                .then(() => {
                    const card = document.querySelector(`.product-card[data-id="${pendingDeleteId}"]`);
                    if (card) {
                        card.style.transition = 'all 0.3s ease';
                        card.style.opacity = '0';
                        card.style.transform = 'scale(0.85)';
                        setTimeout(() => card.remove(), 300);
                    }
                    showToast('محصول با موفقیت حذف شد.');
                    closeDeleteModal();
                })
                .catch(err => {
                    console.error(err);
                    showToast('خطا در حذف محصول!', 'error');
                })
                .finally(() => {
                    confirmBtn.disabled = false;
                    confirmBtn.textContent = 'بله، حذف شود';
                });
        });
    }
});

function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}