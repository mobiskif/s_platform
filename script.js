// Industrial Service Hub — прототип. Общая интерактивность (без бэкенда).

document.addEventListener('DOMContentLoaded', function () {

  // Мобильное меню
  var toggle = document.getElementById('navToggle');
  var nav = document.getElementById('siteNav');
  if (toggle && nav) {
    toggle.addEventListener('click', function () {
      var isOpen = nav.classList.toggle('is-open');
      toggle.classList.toggle('is-open', isOpen);
      toggle.setAttribute('aria-expanded', String(isOpen));
    });
    nav.querySelectorAll('a').forEach(function (link) {
      link.addEventListener('click', function () {
        nav.classList.remove('is-open');
        toggle.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
      });
    });
  }

  // Интерактивный чек-лист приёмки (вкладка «Монтаж и ПНР»)
  document.querySelectorAll('.checklist-item[data-toggle]').forEach(function (item) {
    item.addEventListener('click', function () {
      item.classList.toggle('done');
    });
  });

  // Переключение карточек монтажа (вкладка «Монтаж и ПНР»)
  var installList = document.querySelectorAll('[data-install-item]');
  if (installList.length) {
    installList.forEach(function (el) {
      el.addEventListener('click', function () {
        installList.forEach(function (n) { n.classList.remove('is-active'); });
        el.classList.add('is-active');
        var targetId = el.getAttribute('data-install-item');
        document.querySelectorAll('[data-install-detail]').forEach(function (panel) {
          panel.hidden = panel.getAttribute('data-install-detail') !== targetId;
        });
      });
    });
  }

  // Переключение карточек заявок (вкладка «Заявки и сервис»)
  var ticketList = document.querySelectorAll('[data-ticket]');
  if (ticketList.length) {
    ticketList.forEach(function (el) {
      el.addEventListener('click', function () {
        ticketList.forEach(function (n) { n.classList.remove('is-active'); });
        el.classList.add('is-active');
        var targetId = el.getAttribute('data-ticket');
        document.querySelectorAll('[data-ticket-detail]').forEach(function (panel) {
          panel.hidden = panel.getAttribute('data-ticket-detail') !== targetId;
        });
      });
    });
  }

});
