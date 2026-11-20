'use strict';
const $ = (id) => document.getElementById(id);
let employees = [];
let editing = false;
let busy = false;
let loaded = false;
const money = new Intl.NumberFormat('vi-VN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
const xmlEscape = (value) => String(value).replace(/[<>&"']/g, (char) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;' })[char]);

function message(text, error = false) {
  $('message').textContent = text;
  $('message').classList.toggle('error', error);
  $('message').hidden = false;
}

function setBusy(value) {
  busy = value;
  $('save-button').disabled = value;
  $('refresh-button').disabled = value;
  $('cancel-button').disabled = value;
  for (const button of document.querySelectorAll('.edit-button')) button.disabled = value;
  for (const input of $('employee-form').querySelectorAll('input')) input.disabled = value;
  $('employee-id').disabled = value || editing;
}

function formatXml(xml) { return xml.replace(/>\s*</g, '>\n<'); }

async function soap(operation, body = '') {
  const request = `<?xml version="1.0" encoding="UTF-8"?>\n<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:emp="http://service.employee.fpt.vn/">\n<soap:Body><emp:${operation}>${body}</emp:${operation}></soap:Body>\n</soap:Envelope>`;
  $('soap-operation').textContent = `Phương thức: ${operation} · POST /services/employees`;
  $('soap-request').textContent = formatXml(request);
  $('soap-response').textContent = 'Đang chờ phản hồi…';
  const response = await fetch('/services/employees', {
    method: 'POST', headers: { 'Content-Type': 'text/xml; charset=utf-8', 'SOAPAction': '""' },
    body: request, signal: AbortSignal.timeout(15000)
  });
  const text = await response.text();
  $('soap-response').textContent = formatXml(text);
  $('soap-operation').textContent += ` · HTTP ${response.status}`;
  const xml = new DOMParser().parseFromString(text, 'text/xml');
  if (xml.getElementsByTagName('parsererror').length) throw new Error('Phản hồi SOAP không hợp lệ.');
  const fault = xml.getElementsByTagNameNS('*', 'Fault')[0];
  if (fault) throw new Error(fault.getElementsByTagNameNS('*', 'faultstring')[0]?.textContent || 'Web Service báo lỗi.');
  if (!response.ok) throw new Error(`Không thể gọi dịch vụ (HTTP ${response.status}).`);
  return xml;
}

function render() {
  const keyword = $('search').value.trim().toLocaleLowerCase('vi');
  const visible = employees.filter((e) => String(e.id).includes(keyword) || e.name.toLocaleLowerCase('vi').includes(keyword));
  $('employee-count').textContent = loaded ? String(employees.length) : '—';
  $('list-caption').textContent = loaded ? `Hiển thị ${visible.length} / ${employees.length} nhân viên` : 'Chưa tải được dữ liệu';
  $('employee-list').replaceChildren();
  for (const employee of visible) {
    const row = document.createElement('tr');
    for (const value of [employee.id, employee.name, money.format(employee.salary)]) {
      const cell = document.createElement('td');
      cell.textContent = value;
      row.append(cell);
    }
    row.children[2].className = 'numeric';
    const actions = document.createElement('td');
    const button = document.createElement('button');
    button.className = 'edit-button';
    button.type = 'button';
    button.textContent = 'Chỉnh sửa';
    button.setAttribute('aria-label', `Chỉnh sửa ${employee.name}`);
    button.disabled = busy;
    button.addEventListener('click', () => edit(employee));
    actions.append(button);
    row.append(actions);
    $('employee-list').append(row);
  }
  $('empty-state').hidden = !loaded || visible.length > 0;
  $('empty-state').querySelector('strong').textContent = employees.length ? 'Không tìm thấy nhân viên' : 'Chưa có nhân viên';
  $('empty-state').querySelector('p').textContent = employees.length ? 'Thử tìm bằng mã hoặc tên khác.' : 'Thêm nhân viên đầu tiên bằng biểu mẫu bên trái.';
}

async function loadEmployees() {
  const xml = await soap('getEmployees');
  const field = (node, name) => node.getElementsByTagNameNS('*', name)[0]?.textContent || '';
  employees = [...xml.getElementsByTagNameNS('*', 'employee')].map((node) => ({ id: field(node, 'id'), name: field(node, 'name'), salary: field(node, 'salary') }));
  loaded = true;
  render();
}

function resetForm() {
  editing = false;
  $('employee-form').reset();
  $('employee-id').disabled = busy;
  $('form-title').textContent = 'Thêm nhân viên';
  $('form-caption').textContent = 'Điền thông tin nhân viên mới.';
  $('save-button').textContent = 'Thêm nhân viên';
  $('cancel-button').hidden = true;
}

function edit(employee) {
  if (busy) return;
  editing = true;
  $('employee-id').value = employee.id;
  $('employee-id').disabled = true;
  $('employee-name').value = employee.name;
  $('employee-salary').value = employee.salary;
  $('form-title').textContent = 'Cập nhật nhân viên';
  $('form-caption').textContent = `Đang chỉnh sửa nhân viên #${employee.id}.`;
  $('save-button').textContent = 'Lưu thay đổi';
  $('cancel-button').hidden = false;
  $('employee-name').focus();
}

$('employee-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (busy) return;
  const id = $('employee-id').value;
  const name = $('employee-name').value.trim();
  const salary = $('employee-salary').value;
  if (!name) { message('Vui lòng nhập họ và tên.', true); return; }
  const updating = editing;
  setBusy(true);
  try {
    const xml = await soap(updating ? 'updateEmployee' : 'addEmployees', `<e><id>${xmlEscape(id)}</id><name>${xmlEscape(name)}</name><salary>${xmlEscape(salary)}</salary></e>`);
    if (xml.getElementsByTagNameNS('*', 'success')[0]?.textContent !== 'true') throw new Error('Dịch vụ chưa xác nhận lưu thành công.');
    const mutationTrace = ['soap-operation', 'soap-request', 'soap-response'].map((id) => $(id).textContent);
    resetForm();
    try {
      await loadEmployees();
      message(updating ? 'Đã cập nhật thông tin nhân viên.' : 'Đã thêm nhân viên thành công.');
    } catch (error) {
      message(`Đã lưu nhân viên. Chưa tải lại được danh sách: ${error.message}`, true);
    }
    ['soap-operation', 'soap-request', 'soap-response'].forEach((id, index) => { $(id).textContent = mutationTrace[index]; });
  } catch (error) {
    message(error.name === 'TimeoutError' ? 'Hết thời gian chờ. Hãy tải lại danh sách để kiểm tra kết quả trước khi gửi lại.' : error.message, true);
  } finally { setBusy(false); }
});

$('cancel-button').addEventListener('click', resetForm);
$('search').addEventListener('input', render);
async function refresh() {
  if (busy) return;
  setBusy(true);
  try { await loadEmployees(); $('message').hidden = true; }
  catch (error) { message(`Không tải được danh sách: ${error.message}`, true); render(); }
  finally { setBusy(false); }
}
$('refresh-button').addEventListener('click', refresh);
refresh();
