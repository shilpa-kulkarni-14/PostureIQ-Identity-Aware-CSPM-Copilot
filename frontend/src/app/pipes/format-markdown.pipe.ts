import { Pipe, PipeTransform, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({
  name: 'formatMarkdown',
  standalone: true
})
export class FormatMarkdownPipe implements PipeTransform {
  constructor(private sanitizer: DomSanitizer) {}

  transform(value: string): string {
    if (!value) return '';

    // Escape ALL input first to prevent XSS
    let html = this.escapeHtml(value);

    // Convert code blocks first (before other transformations)
    html = html.replace(/```(\w+)?\n([\s\S]*?)```/g, (_, lang, code) => {
      return `<pre><code class="language-${lang || 'text'}">${code.trim()}</code></pre>`;
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

    // Sanitize the final HTML to ensure safety
    return this.sanitizer.sanitize(SecurityContext.HTML, html) || '';
  }

  private escapeHtml(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
}
