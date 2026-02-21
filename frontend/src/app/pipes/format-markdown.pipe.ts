import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Pipe({
  name: 'formatMarkdown',
  standalone: true
})
export class FormatMarkdownPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(value: string): SafeHtml {
    if (!value) return '';

    let html = value;

    // Convert code blocks first (before other transformations)
    html = html.replace(/```(\w+)?\n([\s\S]*?)```/g, (_, lang, code) => {
      const escapedCode = this.escapeHtml(code.trim());
      return `<pre><code class="language-${lang || 'text'}">${escapedCode}</code></pre>`;
    });

    // Convert inline code
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Convert headers
    html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
    html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
    html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');

    // Convert bold
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

    // Convert italic
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');

    // Convert unordered lists
    html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
    html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');

    // Convert numbered lists
    html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');

    // Convert line breaks (but not inside pre/code blocks)
    html = html.replace(/\n\n/g, '</p><p>');
    html = html.replace(/\n/g, '<br>');

    // Wrap in paragraph if not already wrapped
    if (!html.startsWith('<')) {
      html = `<p>${html}</p>`;
    }

    // Clean up empty paragraphs
    html = html.replace(/<p><\/p>/g, '');
    html = html.replace(/<p><br><\/p>/g, '');

    return this.sanitizer.bypassSecurityTrustHtml(html);
  }

  private escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }
}
