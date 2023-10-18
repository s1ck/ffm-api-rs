use std::{
    any::Any,
    cmp::Ordering,
    ffi::{c_char, CStr, CString},
    ptr::NonNull,
    sync::{Mutex, OnceLock},
};

#[repr(C)]
pub struct Point {
    x: i64,
    y: i64,
}

#[no_mangle]
pub extern "C" fn last_error() -> *const c_char {
    let last_error = with_last_error_mut(|e| e.take());

    last_error
        .flatten()
        .map(|e| e.into_raw())
        .unwrap_or_else(std::ptr::null_mut)
}

#[no_mangle]
pub unsafe extern "C" fn drop_error(error: *mut c_char) {
    if error.is_null() {
        return;
    }

    unsafe {
        let _ = CString::from_raw(error);
    }
}

fn with_last_error_mut<T>(f: impl FnOnce(&mut Option<CString>) -> T) -> Option<T> {
    static LAST_ERROR: OnceLock<Mutex<Option<CString>>> = OnceLock::new();
    let last_error = LAST_ERROR.get_or_init(|| {
        Mutex::new(None)
    });
    let mut last_error = last_error.lock().ok()?;

    Some(f(&mut last_error))
}

fn catch_panic<T>(f: impl FnOnce() -> T) -> Option<T> {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(f));

    match result {
        Ok(result) => Some(result),
        Err(error) => {
            set_error(&*error);
            None
        }
    }
}

fn set_error(error: &(dyn Any + Send)) {
    let error = error_msg(error);
    let error = CString::new(error).expect("CString::new failed");
    let error = Some(error);

    with_last_error_mut(|e| *e = error);
}

fn error_msg(error: &(dyn Any + Send)) -> &str {
    match error.downcast_ref::<&'static str>() {
        Some(s) => s,
        None => match error.downcast_ref::<String>() {
            Some(s) => s.as_str(),
            None => "Unknown error",
        },
    }
}

impl Point {
    fn new(source: i64, target: i64) -> Self {
        Self {
            x: source,
            y: target,
        }
    }

    fn manhattan(&self, other: &Point) -> u64 {
        assert!(self.x < 0 && self.y < 0);
        i64::abs_diff(self.x, other.x) + i64::abs_diff(self.y, other.y)
    }
}

#[no_mangle]
pub extern "C" fn Point_new(source: i64, target: i64) -> Box<Point> {
    let rel = Point::new(source, target);
    Box::new(rel)
}

#[no_mangle]
pub extern "C" fn Point_drop(_r: Option<Box<Point>>) {}


#[no_mangle]
pub extern "C" fn Point_manhattan(p1: Point, p2: Point) -> i64 {
    catch_panic(|| p1.manhattan(&p2) as i64).unwrap_or(-1)
}

#[no_mangle]
pub extern "C" fn Point_operate(operation: extern "C" fn(Point, Point) -> i64) -> i64 {
    let p1 = Point { x: 42, y: 1337 };
    let p2 = Point { x: 1337, y: 1338 };
    operation(p1, p2)
}

#[no_mangle]
pub unsafe extern "C" fn print_string(str: *const libc::c_char) {
    let s = unsafe { CStr::from_ptr(str) };
    let s = s.to_str().expect("to_str failed");
    println!("{s}");
}

#[no_mangle]
pub unsafe extern "C" fn edit_string(str: *mut u8, len: libc::size_t) {
    let str: &mut [u8] = unsafe { std::slice::from_raw_parts_mut(str, len) };
    let c = "❤️";
    str[0..c.len()].copy_from_slice(c.as_bytes());
}

#[no_mangle]
pub extern "C" fn leet() -> i32 {
    1337
}

#[no_mangle]
pub unsafe extern "C" fn callback(func: unsafe extern "C" fn(i64, i64) -> i64) -> i64 {
    let a = 13;
    let b = 37;

    func(a, b)
}

#[no_mangle]
pub unsafe extern "C" fn print_thread_id() {
    println!("{:?}", std::thread::current().id());
}

#[no_mangle]
pub extern "C" fn vec_new() -> Box<Vec<i64>> {
    let v = Vec::<i64>::new();
    Box::new(v)
}

#[no_mangle]
pub extern "C" fn vec_with_capacity(capacity: i64, default_value: i64) -> Box<Vec<i64>> {
    let capacity = capacity as usize;
    let mut v = Vec::<i64>::with_capacity(capacity);
    v.resize(capacity, default_value);
    Box::new(v)
}

#[no_mangle]
pub extern "C" fn vec_set(v: Option<NonNull<Vec<i64>>>, index: i64, value: i64) {
    if let Some(v) = v {
        (unsafe { &mut *v.as_ptr() })[index as usize] = value;
    }
}

#[no_mangle]
pub extern "C" fn vec_get(v: Option<NonNull<Vec<i64>>>, index: i64, default_value: i64) -> i64 {
    v.and_then(|v| (unsafe { &*v.as_ptr() }).get(index as usize).copied())
        .unwrap_or(default_value)
}

#[no_mangle]
pub unsafe extern "C" fn vec_sort_unstable(
    v: Option<NonNull<Vec<i64>>>,
    comp: unsafe extern "C" fn(i64, i64) -> i32,
) {
    if let Some(v) = v {
        let v = unsafe { &mut *v.as_ptr() };
        v.sort_unstable_by(|a, b| match comp(*a, *b) {
            c if c < 0 => Ordering::Less,
            c if c > 0 => Ordering::Greater,
            _ => Ordering::Equal,
        });
    }
}

#[no_mangle]
pub extern "C" fn vec_drop(_v: Box<Vec<i64>>) {}

#[no_mangle]
pub unsafe extern "C" fn ptr_sort_unstable(
    v: *mut i64,
    size: i64,
    comp: unsafe extern "C" fn(i64, i64) -> i32,
) {
    if v.is_null() {
        return;
    }

    let v = std::slice::from_raw_parts_mut(v, size as usize);
    v.sort_unstable_by(|a, b| match comp(*a, *b) {
        c if c < 0 => Ordering::Less,
        c if c > 0 => Ordering::Greater,
        _ => Ordering::Equal,
    });
}

#[no_mangle]
pub extern "C" fn vec_push(v: &'static mut Vec<i64>) {
    v.push(42)
}

#[no_mangle]
pub extern "C" fn vec_print(v: &'static Vec<i64>) {
    v.iter().for_each(|i| println!("{i}"));
}
