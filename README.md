# Lord Sai — Share Market Academy & Lord Sai Investment website

A **frontend-only** static website (HTML, CSS, JavaScript, Bootstrap 5, jQuery). There is no
server, database or API: every page works when served as plain files (GitHub Pages, any static
host, or VS Code Live Server).

The site has two modes that share the same pages:

- **Share Market Academy** (`?mode=academy`): courses, store, blog, stories, testimonials, contact.
- **Lord Sai Investment / Mutual Fund** (`?mode=mutual-fund`): SIP, SWP, insurance, investments, financial planning.

`index.html` is the gateway where visitors pick one of the two.

## Run it locally

Open the folder in VS Code and start **Live Server** on `index.html`, or run any static file
server in the project folder, for example:

```
python -m http.server 5500
```

Then open `http://127.0.0.1:5500/index.html`.

## Project layout

| Path | What it is |
|---|---|
| `*.html` | The pages |
| `css/style.css` | All site styles (Bootstrap is `css/bootstrap.min.css`) |
| `js/main.js` | Shared page behaviour: calculators, sliders, the enquiry pop-up form |
| `js/mode.js` | Share Market / Mutual Fund mode switching |
| `js/lang.js`, `js/translations.js`, `js/i18n/` | English / Hindi / Marathi language switcher |
| `js/enquiry.js` | Sends form enquiries and reviews to the owner on WhatsApp and by email |
| `js/ebook-access.js` | Store page: e-book opened with an access code |
| `js/blog.js`, `js/reviews.js` | Blog search / category filter; testimonials review form |
| `img/`, `pdf/`, `docs/`, `lib/` | Images, PDFs, SWP case-study PDFs, third-party libraries |

## Enquiries on WhatsApp and email

The website has no server. These forms reach the owner in two ways at once:

- the Contact page form (`contact.html`)
- the "Enquire" pop-up on most pages (`#demoModal`, handled in `js/main.js`)
- the "Add Your Review" form on `testimonials.html` (`js/reviews.js`)

1. **WhatsApp (+91 99202 54354):** submitting opens WhatsApp (the app on phones, WhatsApp Web
   or Desktop on computers) with the name, phone, email, interest and message already typed in a
   chat to the owner, and the visitor taps **Send**. WhatsApp does not let a website send a
   message by itself. The form also shows a "Tap here" link in case WhatsApp did not open.
2. **Email (lordsai.academy@gmail.com):** the same details are emailed in the background, even
   if the visitor never sends the WhatsApp message. When the email goes through, the form adds
   "A copy has also been emailed to us".

**Email setup (recommended, works everywhere): Web3Forms key, 2 minutes, free up to 250 emails a month**

1. Open <https://web3forms.com>, enter `lordsai.academy@gmail.com` and click
   **Create Access Key**.
2. The key arrives in that inbox (check Spam). It looks like `a1b2c3d4-e5f6-...`.
3. Paste it into `web3formsKey:` in `js/enquiry.js`:
   ```js
   web3formsKey: "a1b2c3d4-e5f6-...",
   ```
4. Upload the site again (push to GitHub, or upload `js/enquiry.js` to Hostinger).

**Status:** the key for `lordsai.academy@gmail.com` is set (25 Sep 2026). Repeat the steps above
only to change the receiving inbox.

The same key works on every copy of the site: a page opened by double-clicking the HTML file on a
computer, VS Code Live Server, GitHub Pages and Hostinger. There is no activation per website.
The key is designed to be public, so it is safe in the website's code.

**Fallback while no key is set: FormSubmit.** Emails go to `email:` in `js/enquiry.js`
through [FormSubmit](https://formsubmit.co). The first enquiry from each website sends an
**"Activate Form"** email to that inbox, which must be clicked once. It may not work for
pages opened from disk, and some networks block formsubmit.co (for example some VPN or
ad-blocking DNS filters). WhatsApp works in every case.

If an email does not arrive, open the browser console (F12). A line starting with
`[LSI_Enquiry]` gives the reason. To change the WhatsApp number, edit `whatsapp` in the same
settings block.

## Store e-book (access code)

The Share Market Store page shows the e-book without a price or Buy button. A visitor enters
the access code and the e-book opens inside the Store page:

- **Code:** `LSIRA` (upper or lower case both work)
- **PDF location:** `pdf/lsi-ebook.pdf` (currently *Share Market Made Easy*, 164 pages). To
  change the book, replace that file with another PDF of the same name, or change `EBOOK_PDF`
  at the top of `js/ebook-access.js`. If the file is missing, the reader shows "The e-book file
  has not been added yet".
- **Protection:** the reader draws each page as a picture (PDF.js), so it has no Download,
  Print or Copy button. Once the book is unlocked, and only for the e-book:
  - right-click, dragging, selecting and copying are blocked on the book, and so are Ctrl/Cmd + A
    and C when aimed at it (typing fields elsewhere work as usual);
  - while the book is on screen, Ctrl/Cmd + S, P and U, Ctrl+Shift+S (browser screenshot),
    Ctrl+Shift+X, F12 and the developer-tools shortcuts are blocked;
  - printing gives a "cannot be printed" notice instead of the book;
  - the pages blur when the window or tab loses focus, when Print Screen is pressed, and while
    the Windows / Command key is held (Win+PrtSc, Win+Shift+S, Cmd+Shift+3/4/5).

  **No website can block screenshots or screen recording.** Phone screenshot buttons, the
  Snipping Tool, recorders and cameras work outside the browser. Only an installed app can block
  them, using Android's `FLAG_SECURE`. The PDF file itself is also still public at its address,
  and the access code is visible in the page source.
  When the site is opened from disk (file://), browsers do not let the protected reader load
  the file, so the browser's own PDF viewer is used there, with its toolbar hidden.
- **Cover image:** `img/lsi-ebook-cover.jpg` (the front panel of the printed wraparound cover,
  720 × 1022 px), shown on the Store e-book card. To change it, replace that file with a
  portrait image of the same shape.
- To change the code, edit `ACCESS_CODE` in `js/ebook-access.js`.

On computers the browser's own PDF viewer is used. On phones and tablets the pages are drawn
with PDF.js (loaded from cdnjs only when needed).

Because the site has no server, the code and the PDF address can be read by anyone who looks
at the page source. The code keeps casual visitors out; it is not strong protection for a paid
product.

## Courses

The Share Market course page shows the course, fee and curriculum, with the message
"This course will be available soon in our Store." in place of the Buy buttons. The
"Ask a Question First" button opens the enquiry pop-up.

## Languages (English / हिंदी / मराठी)

The language switcher translates all visible text using the dictionaries in `js/i18n/`
(`common.js` plus one file per page). When you add or change text on a page:

1. Add the English text with its Hindi and Marathi translation to that page's file in `js/i18n/`.
2. Increase `I18N_VERSION` in `js/lang.js` so browsers download the new dictionary.

## After changing CSS or JavaScript

Pages load files with a version, for example `css/style.css?v=20260925b`. After editing a file,
change the `?v=` value in the pages that load it so visitors get the new copy.
