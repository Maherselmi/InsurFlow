import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AgentResultListComponent } from './agent-result-list.component';

describe('AgentResultListComponent', () => {
  let component: AgentResultListComponent;
  let fixture: ComponentFixture<AgentResultListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AgentResultListComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AgentResultListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
